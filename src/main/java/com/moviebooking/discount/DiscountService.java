package com.moviebooking.discount;

import com.moviebooking.booking.BookingRepository;
import com.moviebooking.booking.BookingStatus;
import com.moviebooking.common.error.ApiException;
import com.moviebooking.common.error.ErrorCode;
import com.moviebooking.discount.dto.DiscountDtos.DiscountCodeRequest;
import com.moviebooking.discount.dto.DiscountDtos.DiscountCodeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscountService {

    /** Statuses that count against a user's per-code redemption allowance. */
    private static final Set<BookingStatus> COUNTED_STATUSES =
            Set.of(BookingStatus.PENDING_PAYMENT, BookingStatus.CONFIRMED, BookingStatus.CANCELLED);

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final DiscountCodeRepository discountCodeRepository;
    private final BookingRepository bookingRepository;
    private final Clock clock;

    /** Outcome of applying a code to a subtotal. */
    public record AppliedDiscount(DiscountCode code, BigDecimal amount) {
    }

    /**
     * Validates a code against the current time, order size and usage
     * limits, then atomically consumes one redemption. The consumption is a
     * conditional UPDATE, so two concurrent bookings cannot exceed the total
     * usage limit.
     */
    @Transactional
    public AppliedDiscount applyCode(String rawCode, BigDecimal subtotal, Long userId) {
        String normalized = rawCode.trim().toUpperCase(Locale.ROOT);
        DiscountCode code = discountCodeRepository.findByCodeIgnoreCase(normalized)
                .orElseThrow(() -> invalid("Unknown discount code"));

        LocalDateTime now = LocalDateTime.now(clock);
        if (!code.isActive()) {
            throw invalid("This discount code is no longer active");
        }
        if (code.getValidFrom() != null && now.isBefore(code.getValidFrom())) {
            throw invalid("This discount code is not valid yet");
        }
        if (code.getValidUntil() != null && now.isAfter(code.getValidUntil())) {
            throw invalid("This discount code has expired");
        }
        if (code.getMinOrderAmount() != null && subtotal.compareTo(code.getMinOrderAmount()) < 0) {
            throw invalid("Order subtotal is below the minimum of " + code.getMinOrderAmount()
                    + " required for this code");
        }
        if (code.getPerUserLimit() != null) {
            long used = bookingRepository.countByUserIdAndDiscountCodeIgnoreCaseAndStatusIn(
                    userId, code.getCode(), COUNTED_STATUSES);
            if (used >= code.getPerUserLimit()) {
                throw invalid("You have already used this discount code the maximum number of times");
            }
        }
        if (discountCodeRepository.tryConsume(code.getId()) == 0) {
            throw invalid("This discount code has reached its usage limit");
        }
        return new AppliedDiscount(code, computeAmount(code, subtotal));
    }

    /** Returns a redemption when an unpaid booking dies before confirmation. */
    @Transactional
    public void releaseRedemption(String code) {
        discountCodeRepository.findByCodeIgnoreCase(code)
                .ifPresent(found -> discountCodeRepository.releaseOne(found.getId()));
    }

    BigDecimal computeAmount(DiscountCode code, BigDecimal subtotal) {
        BigDecimal amount = switch (code.getType()) {
            case PERCENT -> subtotal.multiply(code.getValue()).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            case FLAT -> code.getValue();
        };
        if (code.getType() == DiscountType.PERCENT && code.getMaxDiscountAmount() != null
                && amount.compareTo(code.getMaxDiscountAmount()) > 0) {
            amount = code.getMaxDiscountAmount();
        }
        if (amount.compareTo(subtotal) > 0) {
            amount = subtotal; // a discount can never push the total below zero
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private ApiException invalid(String message) {
        return ApiException.unprocessable(ErrorCode.DISCOUNT_INVALID, message);
    }

    // ---- Admin management ----

    @Transactional
    public DiscountCodeResponse create(DiscountCodeRequest request) {
        String normalized = request.code().trim().toUpperCase(Locale.ROOT);
        if (discountCodeRepository.findByCodeIgnoreCase(normalized).isPresent()) {
            throw ApiException.conflict(ErrorCode.CONFLICT, "Discount code already exists: " + normalized);
        }
        validateWindow(request);
        DiscountCode code = discountCodeRepository.save(DiscountCode.builder()
                .code(normalized)
                .description(request.description())
                .type(request.type())
                .value(request.value())
                .maxDiscountAmount(request.maxDiscountAmount())
                .minOrderAmount(request.minOrderAmount())
                .validFrom(request.validFrom())
                .validUntil(request.validUntil())
                .totalUsageLimit(request.totalUsageLimit())
                .perUserLimit(request.perUserLimit())
                .active(true)
                .build());
        return DiscountCodeResponse.from(code);
    }

    @Transactional
    public DiscountCodeResponse update(Long id, DiscountCodeRequest request) {
        DiscountCode code = discountCodeRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Discount code not found: " + id));
        validateWindow(request);
        code.setCode(request.code().trim().toUpperCase(Locale.ROOT));
        code.setDescription(request.description());
        code.setType(request.type());
        code.setValue(request.value());
        code.setMaxDiscountAmount(request.maxDiscountAmount());
        code.setMinOrderAmount(request.minOrderAmount());
        code.setValidFrom(request.validFrom());
        code.setValidUntil(request.validUntil());
        code.setTotalUsageLimit(request.totalUsageLimit());
        code.setPerUserLimit(request.perUserLimit());
        return DiscountCodeResponse.from(code);
    }

    @Transactional
    public DiscountCodeResponse deactivate(Long id) {
        DiscountCode code = discountCodeRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Discount code not found: " + id));
        code.setActive(false);
        return DiscountCodeResponse.from(code);
    }

    @Transactional(readOnly = true)
    public List<DiscountCodeResponse> list() {
        return discountCodeRepository.findAll().stream().map(DiscountCodeResponse::from).toList();
    }

    private void validateWindow(DiscountCodeRequest request) {
        if (request.validFrom() != null && request.validUntil() != null
                && request.validUntil().isBefore(request.validFrom())) {
            throw ApiException.badRequest("validUntil must be after validFrom");
        }
        if (request.type() == DiscountType.PERCENT && request.value().compareTo(HUNDRED) > 0) {
            throw ApiException.badRequest("Percentage discount cannot exceed 100");
        }
    }
}
