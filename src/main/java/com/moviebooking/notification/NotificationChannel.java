package com.moviebooking.notification;

/**
 * Delivery abstraction. The take-home ships a logging implementation; a real
 * deployment would plug in an email/SMS/push provider behind this interface.
 */
public interface NotificationChannel {

    /** Delivers the message; throws on failure so the dispatcher can retry. */
    void send(Notification notification);
}
