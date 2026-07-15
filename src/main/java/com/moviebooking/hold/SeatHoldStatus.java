package com.moviebooking.hold;

public enum SeatHoldStatus {
    /** Seats are reserved for this user until {@code expiresAt}. */
    ACTIVE,
    /** The hold was converted into a confirmed booking. */
    CONFIRMED,
    /** The user released the hold explicitly. */
    RELEASED,
    /** The hold timed out and its seats were returned to inventory. */
    EXPIRED
}
