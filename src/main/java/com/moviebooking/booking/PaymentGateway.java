package com.moviebooking.booking;

import java.math.BigDecimal;

/**
 * External payment provider abstraction. Only the mock implementation ships
 * with the take-home; swapping in a real PSP means implementing this
 * interface — the booking flow does not change.
 */
public interface PaymentGateway {

    record ChargeCommand(BigDecimal amount, String currency, PaymentMethod method, boolean simulateFailure) {
    }

    record ChargeResult(boolean success, String transactionId, String failureReason) {

        public static ChargeResult succeeded(String transactionId) {
            return new ChargeResult(true, transactionId, null);
        }

        public static ChargeResult failed(String reason) {
            return new ChargeResult(false, null, reason);
        }
    }

    ChargeResult charge(ChargeCommand command);
}
