package com.moviebooking.refund;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * "Cancel at least {@code minHoursBeforeShow} hours before the show and get
 * {@code refundPercent}% back." A policy holds several of these; the rule
 * with the highest satisfied threshold wins. No satisfied rule = no refund.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class RefundRule {

    @Column(name = "min_hours_before_show", nullable = false)
    private int minHoursBeforeShow;

    @Column(name = "refund_percent", nullable = false)
    private int refundPercent;
}
