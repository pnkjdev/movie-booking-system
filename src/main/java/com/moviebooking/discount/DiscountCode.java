package com.moviebooking.discount;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "discount_codes")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DiscountCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stored uppercase; lookups are case-insensitive. */
    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(length = 300)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DiscountType type;

    /** Named explicitly because VALUE is a reserved word in H2. */
    @Column(name = "discount_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal value;

    /** Cap on the computed discount for PERCENT codes; null = uncapped. */
    @Column(precision = 10, scale = 2)
    private BigDecimal maxDiscountAmount;

    /** Minimum booking subtotal required to apply the code; null = none. */
    @Column(precision = 10, scale = 2)
    private BigDecimal minOrderAmount;

    private LocalDateTime validFrom;

    private LocalDateTime validUntil;

    /** Total redemptions allowed across all users; null = unlimited. */
    private Integer totalUsageLimit;

    /** Redemptions allowed per user; null = unlimited. */
    private Integer perUserLimit;

    @Column(nullable = false)
    @Builder.Default
    private int timesUsed = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
