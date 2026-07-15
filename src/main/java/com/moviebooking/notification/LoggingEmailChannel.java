package com.moviebooking.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LoggingEmailChannel implements NotificationChannel {

    @Override
    public void send(Notification notification) {
        log.info("[EMAIL:{}] to={} subject=\"{}\" body=\"{}\"",
                notification.getType(), notification.getRecipient(),
                notification.getSubject(), notification.getBody());
    }
}
