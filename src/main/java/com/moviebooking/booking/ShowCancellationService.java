package com.moviebooking.booking;

import com.moviebooking.catalog.Show;
import com.moviebooking.catalog.ShowRepository;
import com.moviebooking.catalog.ShowStatus;
import com.moviebooking.common.error.ApiException;
import com.moviebooking.common.error.ErrorCode;
import com.moviebooking.hold.SeatHold;
import com.moviebooking.hold.SeatHoldRepository;
import com.moviebooking.hold.SeatHoldService;
import com.moviebooking.hold.SeatHoldStatus;
import com.moviebooking.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Admin-initiated show cancellation. Every confirmed booking is refunded in
 * full (theater's fault, not the customer's — the refund policy is bypassed)
 * and active holds are force-released. Ordering matters: holds are released
 * BEFORE confirmed bookings are collected, so an in-flight payment either
 * completes first (and is then seen and refunded) or finds its hold dead.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShowCancellationService {

    private final ShowRepository showRepository;
    private final BookingRepository bookingRepository;
    private final SeatHoldRepository seatHoldRepository;
    private final SeatHoldService seatHoldService;
    private final NotificationService notificationService;
    private final Clock clock;

    public record ShowCancellationResult(Long showId, int bookingsRefunded, int holdsReleased) {
    }

    @Transactional
    public ShowCancellationResult cancelShow(Long showId) {
        Show show = showRepository.findByIdWithDetails(showId)
                .orElseThrow(() -> ApiException.notFound("Show not found: " + showId));
        if (show.getStatus() == ShowStatus.CANCELLED) {
            throw ApiException.conflict(ErrorCode.CONFLICT, "Show is already cancelled");
        }
        if (!show.getStartTime().isAfter(LocalDateTime.now(clock))) {
            throw ApiException.conflict(ErrorCode.CONFLICT, "A show that has already started cannot be cancelled");
        }
        show.setStatus(ShowStatus.CANCELLED);

        List<SeatHold> activeHolds = seatHoldRepository.findByShowIdAndStatus(showId, SeatHoldStatus.ACTIVE);
        activeHolds.forEach(hold -> seatHoldService.forceRelease(hold.getId()));

        List<Booking> confirmed = bookingRepository.findByShowIdAndStatusIn(
                showId, List.of(BookingStatus.CONFIRMED));
        for (Booking booking : confirmed) {
            booking.setStatus(BookingStatus.CANCELLED);
            booking.setCancelledAt(clock.instant());
            booking.setRefundPercent(100);
            booking.setRefundAmount(booking.getTotalAmount());
            notificationService.showCancelled(booking);
        }

        log.info("Cancelled show {} — {} booking(s) fully refunded, {} hold(s) released",
                showId, confirmed.size(), activeHolds.size());
        return new ShowCancellationResult(showId, confirmed.size(), activeHolds.size());
    }
}
