package com.moviebooking.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderScheduler {

    private final NotificationService notificationService;

    @Scheduled(fixedDelayString = "${app.jobs.reminder-interval-ms}")
    public void sendShowReminders() {
        int queued = notificationService.queueDueReminders();
        if (queued > 0) {
            log.info("Queued {} show reminder(s)", queued);
        }
    }
}
