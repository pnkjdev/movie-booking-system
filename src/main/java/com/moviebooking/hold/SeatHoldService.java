package com.moviebooking.hold;

import com.moviebooking.booking.Booking;
import com.moviebooking.booking.BookingRepository;
import com.moviebooking.booking.BookingStatus;
import com.moviebooking.catalog.Seat;
import com.moviebooking.catalog.Show;
import com.moviebooking.catalog.ShowRepository;
import com.moviebooking.catalog.ShowSeat;
import com.moviebooking.catalog.ShowSeatRepository;
import com.moviebooking.catalog.ShowSeatStatus;
import com.moviebooking.catalog.ShowStatus;
import com.moviebooking.common.error.ApiException;
import com.moviebooking.common.error.ErrorCode;
import com.moviebooking.config.AppProperties;
import com.moviebooking.discount.DiscountService;
import com.moviebooking.pricing.PriceQuote;
import com.moviebooking.pricing.PricingService;
import com.moviebooking.auth.UserRepository;
import com.moviebooking.auth.User;
import com.moviebooking.hold.dto.HoldDtos.HoldRequest;
import com.moviebooking.hold.dto.HoldDtos.HoldResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Owns the seat-hold lifecycle: acquisition under pessimistic row locks,
 * explicit release, and expiry. All seat state transitions in the system
 * funnel through ordered {@code SELECT ... FOR UPDATE} locks, which is what
 * guarantees two users can never hold or book the same seat.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatHoldService {

    private final SeatHoldRepository seatHoldRepository;
    private final ShowSeatRepository showSeatRepository;
    private final ShowRepository showRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final DiscountService discountService;
    private final PricingService pricingService;
    private final AppProperties properties;
    private final Clock clock;

    @Transactional
    public HoldResponse createHold(Long userId, HoldRequest request) {
        Show show = showRepository.findByIdWithDetails(request.showId())
                .orElseThrow(() -> ApiException.notFound("Show not found: " + request.showId()));
        requireBookable(show);

        Set<Long> seatIds = new LinkedHashSet<>(request.seatIds());
        int maxSeats = properties.booking().maxSeatsPerHold();
        if (seatIds.size() > maxSeats) {
            throw ApiException.badRequest("At most " + maxSeats + " seats can be held at once");
        }

        // Lock acquisition point: rows come back locked, in id order.
        List<ShowSeat> lockedSeats = showSeatRepository.lockByShowIdAndSeatIds(show.getId(), seatIds);
        if (lockedSeats.size() != seatIds.size()) {
            throw ApiException.badRequest("One or more seats do not belong to this show");
        }

        Instant now = clock.instant();
        List<String> unavailable = new ArrayList<>();
        for (ShowSeat showSeat : lockedSeats) {
            if (!isEffectivelyAvailable(showSeat, now)) {
                unavailable.add(showSeat.getSeat().label());
            }
        }
        if (!unavailable.isEmpty()) {
            throw ApiException.conflict(ErrorCode.SEAT_UNAVAILABLE,
                    "Some seats are no longer available", unavailable);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found: " + userId));
        SeatHold hold = seatHoldRepository.save(SeatHold.builder()
                .user(user)
                .show(show)
                .status(SeatHoldStatus.ACTIVE)
                .expiresAt(now.plus(Duration.ofSeconds(properties.booking().holdTtlSeconds())))
                .createdAt(now)
                .build());
        lockedSeats.forEach(showSeat -> showSeat.markHeld(hold));

        log.info("User {} held {} seat(s) for show {} until {}",
                userId, lockedSeats.size(), show.getId(), hold.getExpiresAt());
        return toResponse(hold, lockedSeats.stream().map(ShowSeat::getSeat).toList(), now);
    }

    @Transactional(readOnly = true)
    public HoldResponse getHold(Long userId, Long holdId) {
        SeatHold hold = seatHoldRepository.findById(holdId)
                .orElseThrow(() -> ApiException.notFound("Hold not found: " + holdId));
        requireOwner(hold, userId);
        List<Seat> seats = showSeatRepository.findByHoldId(holdId).stream().map(ShowSeat::getSeat).toList();
        return toResponse(hold, seats, clock.instant());
    }

    /** Customer voluntarily gives the seats back before expiry. */
    @Transactional
    public void releaseHold(Long userId, Long holdId) {
        SeatHold hold = seatHoldRepository.lockById(holdId)
                .orElseThrow(() -> ApiException.notFound("Hold not found: " + holdId));
        requireOwner(hold, userId);
        if (hold.getStatus() != SeatHoldStatus.ACTIVE) {
            throw ApiException.conflict(ErrorCode.HOLD_INVALID,
                    "Hold is not active (current status: " + hold.getStatus() + ")");
        }
        terminate(hold, SeatHoldStatus.RELEASED);
        log.info("User {} released hold {}", userId, holdId);
    }

    /**
     * Called by the sweeper for each timed-out hold. Runs in its own
     * transaction and re-checks state under the hold row lock, so it can
     * never race a payment that is confirming the same hold.
     */
    @Transactional
    public void expireHold(Long holdId) {
        SeatHold hold = seatHoldRepository.lockById(holdId).orElse(null);
        if (hold == null || hold.getStatus() != SeatHoldStatus.ACTIVE || !hold.isExpired(clock.instant())) {
            return; // already confirmed/released, or expiry no longer applies
        }
        terminate(hold, SeatHoldStatus.EXPIRED);
        log.info("Expired hold {} for show {}", holdId, hold.getShow().getId());
    }

    /**
     * Administrative release (used when a show is cancelled): terminates the
     * hold regardless of expiry, if it is still active. Serializes on the
     * hold row lock with any in-flight payment for the same hold.
     */
    @Transactional
    public void forceRelease(Long holdId) {
        SeatHold hold = seatHoldRepository.lockById(holdId).orElse(null);
        if (hold == null || hold.getStatus() != SeatHoldStatus.ACTIVE) {
            return;
        }
        terminate(hold, SeatHoldStatus.RELEASED);
        log.info("Force-released hold {} (show cancellation)", holdId);
    }

    /**
     * Releases the hold's seats (only those still pointing at this hold — a
     * stale-hold seat may have been legitimately reclaimed by someone else),
     * then expires any unpaid booking created from it.
     */
    private void terminate(SeatHold hold, SeatHoldStatus finalStatus) {
        List<ShowSeat> seats = showSeatRepository.lockByHoldId(hold.getId());
        seats.forEach(ShowSeat::release);
        hold.setStatus(finalStatus);

        bookingRepository.findByHoldId(hold.getId())
                .filter(booking -> booking.getStatus() == BookingStatus.PENDING_PAYMENT)
                .ifPresent(this::expirePendingBooking);
    }

    private void expirePendingBooking(Booking booking) {
        booking.setStatus(BookingStatus.EXPIRED);
        if (booking.getDiscountCode() != null) {
            discountService.releaseRedemption(booking.getDiscountCode());
        }
        log.info("Expired unpaid booking {} ({})", booking.getId(), booking.getReference());
    }

    private boolean isEffectivelyAvailable(ShowSeat showSeat, Instant now) {
        if (showSeat.getStatus() == ShowSeatStatus.AVAILABLE) {
            return true;
        }
        // A held seat whose hold has lapsed can be reclaimed immediately;
        // the sweeper will still tidy up the old hold record later.
        return showSeat.getStatus() == ShowSeatStatus.HELD
                && (showSeat.getHold() == null || showSeat.getHold().isExpired(now));
    }

    private void requireBookable(Show show) {
        if (show.getStatus() != ShowStatus.SCHEDULED) {
            throw ApiException.conflict(ErrorCode.SHOW_NOT_BOOKABLE, "Show is cancelled");
        }
        if (!show.getStartTime().isAfter(LocalDateTime.now(clock))) {
            throw ApiException.conflict(ErrorCode.SHOW_NOT_BOOKABLE, "Show has already started");
        }
    }

    private void requireOwner(SeatHold hold, Long userId) {
        if (!hold.getUser().getId().equals(userId)) {
            throw ApiException.forbidden("This hold belongs to another user");
        }
    }

    private HoldResponse toResponse(SeatHold hold, List<Seat> seats, Instant now) {
        PriceQuote quote = pricingService.quote(hold.getShow(), seats);
        return HoldResponse.from(hold, quote, properties.currency(), now);
    }
}
