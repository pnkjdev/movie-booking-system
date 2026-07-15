package com.moviebooking.discount.dto;

import com.moviebooking.discount.DiscountCode;
import com.moviebooking.discount.DiscountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class DiscountDtos {

    private DiscountDtos() {
    }

    public record DiscountCodeRequest(
            @NotBlank @Size(min = 3, max = 40) String code,
            @Size(max = 300) String description,
            @NotNull DiscountType type,
            @NotNull @Positive BigDecimal value,
            @Positive BigDecimal maxDiscountAmount,
            @Positive BigDecimal minOrderAmount,
            LocalDateTime validFrom,
            LocalDateTime validUntil,
            @Positive Integer totalUsageLimit,
            @Positive Integer perUserLimit
    ) {
    }

    public record DiscountCodeResponse(
            Long id,
            String code,
            String description,
            DiscountType type,
            BigDecimal value,
            BigDecimal maxDiscountAmount,
            BigDecimal minOrderAmount,
            LocalDateTime validFrom,
            LocalDateTime validUntil,
            Integer totalUsageLimit,
            Integer perUserLimit,
            int timesUsed,
            boolean active
    ) {

        public static DiscountCodeResponse from(DiscountCode code) {
            return new DiscountCodeResponse(code.getId(), code.getCode(), code.getDescription(),
                    code.getType(), code.getValue(), code.getMaxDiscountAmount(), code.getMinOrderAmount(),
                    code.getValidFrom(), code.getValidUntil(), code.getTotalUsageLimit(),
                    code.getPerUserLimit(), code.getTimesUsed(), code.isActive());
        }
    }
}
