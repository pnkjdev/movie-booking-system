package com.moviebooking.common.error;

/**
 * Machine-readable error codes returned in every error payload so clients can
 * branch on failure type without parsing human-readable messages.
 */
public enum ErrorCode {
    VALIDATION_ERROR,
    BAD_REQUEST,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    SEAT_UNAVAILABLE,
    HOLD_EXPIRED,
    HOLD_INVALID,
    SHOW_NOT_BOOKABLE,
    DISCOUNT_INVALID,
    PAYMENT_FAILED,
    BOOKING_NOT_CANCELLABLE,
    CONCURRENT_MODIFICATION,
    INTERNAL_ERROR
}
