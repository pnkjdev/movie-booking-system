package com.moviebooking.refund;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RefundCalculatorTest {

    private final RefundCalculator calculator = new RefundCalculator();

    private final List<RefundRule> standardRules = List.of(
            new RefundRule(48, 100),
            new RefundRule(24, 75),
            new RefundRule(2, 50));

    @Test
    @DisplayName("cancelling far ahead earns the most generous tier")
    void fullRefundTier() {
        var outcome = calculator.compute(standardRules, new BigDecimal("810.00"), 72);
        assertThat(outcome.percent()).isEqualTo(100);
        assertThat(outcome.amount()).isEqualByComparingTo("810.00");
    }

    @Test
    @DisplayName("middle tier applies between thresholds")
    void midTier() {
        var outcome = calculator.compute(standardRules, new BigDecimal("810.00"), 30);
        assertThat(outcome.percent()).isEqualTo(75);
        assertThat(outcome.amount()).isEqualByComparingTo("607.50");
    }

    @Test
    @DisplayName("exactly at a threshold qualifies for that tier")
    void boundaryInclusive() {
        var outcome = calculator.compute(standardRules, new BigDecimal("100.00"), 24.0);
        assertThat(outcome.percent()).isEqualTo(75);
    }

    @Test
    @DisplayName("just under a threshold falls to the next tier")
    void justUnderThreshold() {
        var outcome = calculator.compute(standardRules, new BigDecimal("100.00"), 23.98);
        assertThat(outcome.percent()).isEqualTo(50);
    }

    @Test
    @DisplayName("cancelling inside the last cutoff refunds nothing")
    void noRefundInsideCutoff() {
        var outcome = calculator.compute(standardRules, new BigDecimal("810.00"), 1.5);
        assertThat(outcome.percent()).isZero();
        assertThat(outcome.amount()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("no rules means no refund")
    void emptyRules() {
        var outcome = calculator.compute(List.of(), new BigDecimal("810.00"), 100);
        assertThat(outcome.percent()).isZero();
        assertThat(outcome.amount()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("refund amounts round half-up to two decimals")
    void rounding() {
        var outcome = calculator.compute(List.of(new RefundRule(0, 33)), new BigDecimal("100.01"), 5);
        // 100.01 * 0.33 = 33.0033 -> 33.00
        assertThat(outcome.amount()).isEqualByComparingTo("33.00");
    }
}
