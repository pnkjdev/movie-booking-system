package com.moviebooking.refund;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin-configurable cancellation refund policy. Theaters may carry their own
 * policy; otherwise the single default policy applies.
 */
@Entity
@Table(name = "refund_policies")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RefundPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(length = 300)
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /** Exactly one policy should be the system default at a time. */
    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private boolean defaultPolicy = false;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "refund_policy_rules", joinColumns = @JoinColumn(name = "policy_id"))
    @OrderBy("minHoursBeforeShow DESC")
    @Builder.Default
    private List<RefundRule> rules = new ArrayList<>();
}
