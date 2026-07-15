package com.moviebooking.notification;

import com.moviebooking.booking.Booking;
import com.moviebooking.booking.BookingRepository;
import com.moviebooking.booking.BookingSeat;
import com.moviebooking.booking.BookingStatus;
import com.moviebooking.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Writes notification outbox rows inside the caller's business transaction
 * (so a rolled-back booking never notifies anyone) and nudges the async
 * dispatcher after commit (so the booking flow never waits on delivery).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationDispatcher dispatcher;
    private final BookingRepository bookingRepository;
    private final AppProperties properties;
    private final Clock clock;

    public void bookingConfirmed(Booking booking) {
        enqueue(booking, NotificationType.BOOKING_CONFIRMED,
                "Booking confirmed: " + booking.getReference(),
                "Your booking %s for %s is confirmed. %s | Seats: %s | Amount paid: %s %s.".formatted(
                        booking.getReference(), movieTitle(booking), showTime(booking),
                        seatLabels(booking), booking.getCurrency(), booking.getTotalAmount()));
    }

    public void bookingCancelled(Booking booking, BigDecimal refundAmount) {
        enqueue(booking, NotificationType.BOOKING_CANCELLED,
                "Booking cancelled: " + booking.getReference(),
                "Your booking %s for %s has been cancelled. Refund: %s %s.".formatted(
                        booking.getReference(), movieTitle(booking),
                        booking.getCurrency(), refundAmount));
    }

    public void paymentFailed(Booking booking, String reason) {
        enqueue(booking, NotificationType.PAYMENT_FAILED,
                "Payment failed for booking " + booking.getReference(),
                "Payment for booking %s failed (%s). Your seats stay reserved until the hold expires — please retry.".formatted(
                        booking.getReference(), reason));
    }

    public void showCancelled(Booking booking) {
        enqueue(booking, NotificationType.SHOW_CANCELLED,
                "Show cancelled — booking " + booking.getReference() + " fully refunded",
                "The show %s (%s) was cancelled by the theater. Your booking %s is fully refunded: %s %s.".formatted(
                        movieTitle(booking), showTime(booking), booking.getReference(),
                        booking.getCurrency(), booking.getTotalAmount()));
    }

    /**
     * Queues SHOW_REMINDER notifications for confirmed bookings whose show
     * starts within the configured lead window. Invoked by the scheduler.
     */
    @Transactional
    public int queueDueReminders() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime windowEnd = now.plusMinutes(properties.jobs().reminderLeadMinutes());
        List<Booking> due = bookingRepository.findDueForReminder(BookingStatus.CONFIRMED, now, windowEnd);
        for (Booking booking : due) {
            booking.setReminderSentAt(clock.instant());
            enqueue(booking, NotificationType.SHOW_REMINDER,
                    "Reminder: " + movieTitle(booking) + " starts soon",
                    "Reminder for booking %s: %s starts at %s. Seats: %s. Enjoy the show!".formatted(
                            booking.getReference(), movieTitle(booking), booking.getShow().getStartTime(),
                            seatLabels(booking)));
        }
        return due.size();
    }

    private void enqueue(Booking booking, NotificationType type, String subject, String body) {
        notificationRepository.save(Notification.builder()
                .user(booking.getUser())
                .booking(booking)
                .type(type)
                .recipient(booking.getUser().getEmail())
                .subject(subject)
                .body(body)
                .status(NotificationStatus.PENDING)
                .createdAt(clock.instant())
                .build());
        triggerDispatchAfterCommit();
    }

    private void triggerDispatchAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatcher.dispatchPendingAsync();
                }
            });
        } else {
            dispatcher.dispatchPendingAsync();
        }
    }

    private String movieTitle(Booking booking) {
        return booking.getShow().getMovie().getTitle();
    }

    private String showTime(Booking booking) {
        return "Show time: " + booking.getShow().getStartTime();
    }

    private String seatLabels(Booking booking) {
        return booking.getSeats().stream()
                .map(BookingSeat::getSeatLabel)
                .collect(Collectors.joining(", "));
    }
}
