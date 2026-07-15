package com.moviebooking.refund;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Pure refund math: given a policy's rules and how far ahead of the show the
 * cancellation happens, picks the most generous satisfied threshold.
 */
@Component
public class RefundCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public record RefundOutcome(int percent, BigDecimal amount) {

        public static RefundOutcome none(BigDecimal zeroScale) {
            return new RefundOutcome(0, zeroScale);
        }
    }

    /**
     * @param rules            policy rules ("cancel >= N hours before -> P% back")
     * @param totalPaid        amount actually paid for the booking
     * @param hoursBeforeShow  hours between cancellation time and showtime
     *                         (fractions matter near thresholds)
     */
    public RefundOutcome compute(List<RefundRule> rules, BigDecimal totalPaid, double hoursBeforeShow) {
        int bestPercent = 0;
        int bestThreshold = -1;
        if (rules != null) {
            for (RefundRule rule : rules) {
                if (hoursBeforeShow >= rule.getMinHoursBeforeShow()
                        && rule.getMinHoursBeforeShow() > bestThreshold) {
                    bestThreshold = rule.getMinHoursBeforeShow();
                    bestPercent = rule.getRefundPercent();
                }
            }
        }
        BigDecimal amount = totalPaid
                .multiply(BigDecimal.valueOf(bestPercent))
                .divide(HUNDRED, 2, RoundingMode.HALF_UP);
        return new RefundOutcome(bestPercent, amount);
    }
}
