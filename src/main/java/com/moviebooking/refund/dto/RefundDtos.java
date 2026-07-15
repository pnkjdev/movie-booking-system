package com.moviebooking.refund.dto;

import com.moviebooking.refund.RefundPolicy;
import com.moviebooking.refund.RefundRule;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Comparator;
import java.util.List;

public final class RefundDtos {

    private RefundDtos() {
    }

    public record RefundRuleDto(
            @Min(0) @Max(8760) int minHoursBeforeShow,
            @Min(0) @Max(100) int refundPercent
    ) {

        public static RefundRuleDto from(RefundRule rule) {
            return new RefundRuleDto(rule.getMinHoursBeforeShow(), rule.getRefundPercent());
        }
    }

    public record RefundPolicyRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 300) String description,
            @NotEmpty @Size(max = 10) List<@Valid RefundRuleDto> rules,
            boolean defaultPolicy
    ) {
    }

    public record RefundPolicyResponse(
            Long id,
            String name,
            String description,
            boolean active,
            boolean defaultPolicy,
            List<RefundRuleDto> rules
    ) {

        public static RefundPolicyResponse from(RefundPolicy policy) {
            return new RefundPolicyResponse(policy.getId(), policy.getName(), policy.getDescription(),
                    policy.isActive(), policy.isDefaultPolicy(),
                    policy.getRules().stream()
                            .map(RefundRuleDto::from)
                            .sorted(Comparator.comparingInt(RefundRuleDto::minHoursBeforeShow).reversed())
                            .toList());
        }
    }
}
