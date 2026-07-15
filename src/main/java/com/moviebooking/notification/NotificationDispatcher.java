package com.moviebooking.notification;

import com.moviebooking.config.AsyncConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Drains the notification outbox off the request thread. Triggered eagerly
 * (async, right after a business transaction commits) and by a scheduled
 * fallback that also retries transient channel failures.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatcher {

    private static final int MAX_ATTEMPTS = 3;

    private final NotificationRepository notificationRepository;
    private final NotificationChannel channel;
    private final Clock clock;

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    public void dispatchPendingAsync() {
        dispatchPending();
    }

    @Scheduled(fixedDelayString = "${app.jobs.notification-dispatch-interval-ms}")
    public void dispatchOnSchedule() {
        dispatchPending();
    }

    /**
     * Synchronized because the eager async trigger and the scheduled pass can
     * overlap on a single node; this keeps at-most-once delivery per row.
     */
    synchronized void dispatchPending() {
        for (Notification notification : notificationRepository
                .findTop50ByStatusOrderByIdAsc(NotificationStatus.PENDING)) {
            try {
                channel.send(notification);
                notification.setStatus(NotificationStatus.SENT);
                notification.setSentAt(clock.instant());
            } catch (Exception ex) {
                notification.setAttempts(notification.getAttempts() + 1);
                notification.setLastError(ex.getMessage());
                if (notification.getAttempts() >= MAX_ATTEMPTS) {
                    notification.setStatus(NotificationStatus.FAILED);
                    log.error("Notification {} permanently failed after {} attempts",
                            notification.getId(), notification.getAttempts());
                }
            }
            notificationRepository.save(notification);
        }
    }
}
