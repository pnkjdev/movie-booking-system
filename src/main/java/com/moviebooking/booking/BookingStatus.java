package com.moviebooking.booking;

public enum BookingStatus {
    /** Created from an active hold; awaiting successful payment. */
    PENDING_PAYMENT,
    /** Paid; seats are allocated to this booking. */
    CONFIRMED,
    /** Cancelled by the customer or by an admin show cancellation. */
    CANCELLED,
    /** The underlying hold expired before payment completed. */
    EXPIRED
}
