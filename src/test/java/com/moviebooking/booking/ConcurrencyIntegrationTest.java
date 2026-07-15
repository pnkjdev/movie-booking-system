package com.moviebooking.booking;

import com.moviebooking.auth.User;
import com.moviebooking.booking.dto.BookingDtos.BookingResponse;
import com.moviebooking.booking.dto.BookingDtos.CreateBookingRequest;
import com.moviebooking.catalog.ShowSeatStatus;
import com.moviebooking.common.error.ApiException;
import com.moviebooking.common.error.ErrorCode;
import com.moviebooking.discount.DiscountCode;
import com.moviebooking.discount.DiscountType;
import com.moviebooking.hold.SeatHoldStatus;
import com.moviebooking.hold.dto.HoldDtos.HoldRequest;
import com.moviebooking.hold.dto.HoldDtos.HoldResponse;
import com.moviebooking.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The requirement's core guarantee: many users racing for the same seats
 * must be serialized with no double allocation. These tests run real
 * concurrent transactions against the database.
 */
class ConcurrencyIntegrationTest extends IntegrationTestBase {

    private static final int RACERS = 8;

    @Test
    @DisplayName("N users race for the same seat: exactly one hold wins, everyone else gets SEAT_UNAVAILABLE")
    void singleSeatRace() throws Exception {
        var bundle = factory.catalogWithShow(48);
        Long contestedSeatId = bundle.seat(0).getId();
        List<User> users = new ArrayList<>();
        for (int i = 0; i < RACERS; i++) {
            users.add(factory.customer());
        }

        List<Object> outcomes = race(users.stream()
                .map(user -> (Callable<Object>) () ->
                        seatHoldService.createHold(user.getId(),
                                new HoldRequest(bundle.showId(), List.of(contestedSeatId))))
                .toList());

        List<HoldResponse> wins = outcomes.stream()
                .filter(HoldResponse.class::isInstance).map(HoldResponse.class::cast).toList();
        List<ApiException> losses = outcomes.stream()
                .filter(ApiException.class::isInstance).map(ApiException.class::cast).toList();

        assertThat(wins).hasSize(1);
        assertThat(losses).hasSize(RACERS - 1);
        assertThat(losses).allSatisfy(loss ->
                assertThat(loss.getCode()).isIn(ErrorCode.SEAT_UNAVAILABLE, ErrorCode.CONCURRENT_MODIFICATION));

        // Database ground truth: one active hold, seat held exactly once.
        assertThat(seatHoldRepository.findByShowIdAndStatus(bundle.showId(), SeatHoldStatus.ACTIVE)).hasSize(1);
        assertThat(showSeatRepository.findByShowIdWithSeat(bundle.showId()))
                .filteredOn(seat -> seat.getSeat().getId().equals(contestedSeatId))
                .singleElement()
                .satisfies(seat -> assertThat(seat.getStatus()).isEqualTo(ShowSeatStatus.HELD));
    }

    @Test
    @DisplayName("overlapping multi-seat requests never split a seat between two holds")
    void overlappingSeatSetsRace() throws Exception {
        var bundle = factory.catalogWithShow(48);
        User first = factory.customer();
        User second = factory.customer();
        // Requests overlap on seat index 1; lock ordering by id must make
        // one request take both of its seats and the other fail cleanly.
        List<Long> setA = List.of(bundle.seat(0).getId(), bundle.seat(1).getId());
        List<Long> setB = List.of(bundle.seat(1).getId(), bundle.seat(2).getId());

        List<Object> outcomes = race(List.of(
                () -> seatHoldService.createHold(first.getId(), new HoldRequest(bundle.showId(), setA)),
                () -> seatHoldService.createHold(second.getId(), new HoldRequest(bundle.showId(), setB))));

        long wins = outcomes.stream().filter(HoldResponse.class::isInstance).count();
        assertThat(wins).isEqualTo(1);

        // No seat may be HELD without belonging to the single winning hold.
        var showSeats = showSeatRepository.findByShowIdWithSeat(bundle.showId());
        var heldSeats = showSeats.stream()
                .filter(seat -> seat.getStatus() == ShowSeatStatus.HELD).toList();
        assertThat(heldSeats).hasSize(2);
        assertThat(heldSeats.stream().map(seat -> seat.getHold().getId()).distinct()).hasSize(1);
    }

    @Test
    @DisplayName("a discount code with one remaining redemption cannot be consumed twice concurrently")
    void discountUsageLimitRace() throws Exception {
        var bundle = factory.catalogWithShow(48);
        User first = factory.customer();
        User second = factory.customer();
        DiscountCode code = factory.discount("RACE", DiscountType.FLAT, "50.00", 1, null);

        HoldResponse holdA = holdSeats(first, bundle, 0);
        HoldResponse holdB = holdSeats(second, bundle, 1);

        List<Object> outcomes = race(List.of(
                () -> bookingService.createBooking(first.getId(),
                        new CreateBookingRequest(holdA.id(), code.getCode())),
                () -> bookingService.createBooking(second.getId(),
                        new CreateBookingRequest(holdB.id(), code.getCode()))));

        long discounted = outcomes.stream()
                .filter(BookingResponse.class::isInstance).map(BookingResponse.class::cast)
                .filter(booking -> booking.discountAmount().signum() > 0)
                .count();
        long rejected = outcomes.stream()
                .filter(ApiException.class::isInstance).map(ApiException.class::cast)
                .filter(ex -> ex.getCode() == ErrorCode.DISCOUNT_INVALID)
                .count();

        assertThat(discounted).isEqualTo(1);
        assertThat(rejected).isEqualTo(1);
        assertThat(discountCodeRepository.findById(code.getId()).orElseThrow().getTimesUsed()).isEqualTo(1);
    }

    /** Runs all tasks with a start barrier; returns results or thrown ApiExceptions. */
    private List<Object> race(List<Callable<Object>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await(10, TimeUnit.SECONDS);
                    try {
                        return task.call();
                    } catch (ApiException ex) {
                        return ex;
                    }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> future : futures) {
                outcomes.add(future.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }
}
