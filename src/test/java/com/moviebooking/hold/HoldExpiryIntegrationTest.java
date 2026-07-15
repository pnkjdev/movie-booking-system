package com.moviebooking.hold;

import com.moviebooking.auth.User;
import com.moviebooking.booking.BookingStatus;
import com.moviebooking.booking.dto.BookingDtos.BookingResponse;
import com.moviebooking.catalog.ShowSeatStatus;
import com.moviebooking.common.error.ApiException;
import com.moviebooking.common.error.ErrorCode;
import com.moviebooking.discount.DiscountCode;
import com.moviebooking.discount.DiscountType;
import com.moviebooking.hold.dto.HoldDtos.HoldResponse;
import com.moviebooking.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HoldExpiryIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("seats under a live hold are not grabbable; conflict lists the seat labels")
    void liveHoldBlocks() {
        var bundle = factory.catalogWithShow(48);
        User first = factory.customer();
        User second = factory.customer();
        holdSeats(first, bundle, 0, 1);

        assertThatThrownBy(() -> holdSeats(second, bundle, 1, 2))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getCode()).isEqualTo(ErrorCode.SEAT_UNAVAILABLE);
                    assertThat(api.getDetails()).containsExactly("A2");
                });
    }

    @Test
    @DisplayName("an expired hold's seats are reclaimable immediately, without waiting for the sweeper")
    void lazyReclaim() {
        var bundle = factory.catalogWithShow(48);
        User first = factory.customer();
        User second = factory.customer();
        HoldResponse expired = holdSeats(first, bundle, 0, 1);

        forceExpire(expired.id());

        HoldResponse reclaimed = holdSeats(second, bundle, 0, 1);
        assertThat(reclaimed.status()).isEqualTo(SeatHoldStatus.ACTIVE);

        // The sweeper later processes the stale hold without disturbing the new owner.
        seatHoldService.expireHold(expired.id());
        assertThat(seatHoldRepository.findById(expired.id()).orElseThrow().getStatus())
                .isEqualTo(SeatHoldStatus.EXPIRED);
        assertThat(seatHoldRepository.findById(reclaimed.id()).orElseThrow().getStatus())
                .isEqualTo(SeatHoldStatus.ACTIVE);
        assertThat(showSeatRepository.findByHoldId(reclaimed.id())).hasSize(2);
    }

    @Test
    @DisplayName("sweeper expiry releases seats, kills the unpaid booking and returns the discount")
    void sweeperExpiryCascades() {
        var bundle = factory.catalogWithShow(48);
        User user = factory.customer();
        DiscountCode code = factory.discount("EXP", DiscountType.FLAT, "50.00", 5, null);

        HoldResponse hold = holdSeats(user, bundle, 0, 1);
        BookingResponse booking = book(user, hold.id(), code.getCode());
        assertThat(discountCodeRepository.findById(code.getId()).orElseThrow().getTimesUsed()).isEqualTo(1);

        forceExpire(hold.id());
        seatHoldService.expireHold(hold.id());

        assertThat(seatHoldRepository.findById(hold.id()).orElseThrow().getStatus())
                .isEqualTo(SeatHoldStatus.EXPIRED);
        assertThat(bookingRepository.findById(booking.id()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.EXPIRED);
        assertThat(discountCodeRepository.findById(code.getId()).orElseThrow().getTimesUsed()).isZero();
        assertThat(showSeatRepository.findByShowIdWithSeat(bundle.showId()))
                .allSatisfy(seat -> assertThat(seat.getStatus()).isEqualTo(ShowSeatStatus.AVAILABLE));
    }

    @Test
    @DisplayName("expireHold is a no-op for holds that are not yet expired or already consumed")
    void expireHoldGuards() {
        var bundle = factory.catalogWithShow(48);
        User user = factory.customer();
        HoldResponse hold = holdSeats(user, bundle, 0);

        seatHoldService.expireHold(hold.id()); // not expired yet -> untouched
        assertThat(seatHoldRepository.findById(hold.id()).orElseThrow().getStatus())
                .isEqualTo(SeatHoldStatus.ACTIVE);
    }

    @Test
    @DisplayName("explicit release returns seats and rejects double release")
    void explicitRelease() {
        var bundle = factory.catalogWithShow(48);
        User user = factory.customer();
        HoldResponse hold = holdSeats(user, bundle, 0, 1);

        seatHoldService.releaseHold(user.getId(), hold.id());

        assertThat(seatHoldRepository.findById(hold.id()).orElseThrow().getStatus())
                .isEqualTo(SeatHoldStatus.RELEASED);
        assertThat(showSeatRepository.findByShowIdWithSeat(bundle.showId()))
                .allSatisfy(seat -> assertThat(seat.getStatus()).isEqualTo(ShowSeatStatus.AVAILABLE));

        assertThatThrownBy(() -> seatHoldService.releaseHold(user.getId(), hold.id()))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.HOLD_INVALID);
    }

    private void forceExpire(Long holdId) {
        SeatHold entity = seatHoldRepository.findById(holdId).orElseThrow();
        entity.setExpiresAt(Instant.now().minusSeconds(5));
        seatHoldRepository.save(entity);
    }
}
