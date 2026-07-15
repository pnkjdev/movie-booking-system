package com.moviebooking.discount;

import com.moviebooking.booking.BookingRepository;
import com.moviebooking.common.error.ApiException;
import com.moviebooking.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiscountServiceTest {

    private static final Instant NOW = LocalDateTime.of(2026, 7, 8, 12, 0).toInstant(ZoneOffset.UTC);

    @Mock
    private DiscountCodeRepository discountCodeRepository;

    @Mock
    private BookingRepository bookingRepository;

    private DiscountService discountService;

    @BeforeEach
    void setUp() {
        discountService = new DiscountService(discountCodeRepository, bookingRepository,
                Clock.fixed(NOW, ZoneId.of("UTC")));
    }

    // ---- amount math ----

    @Test
    @DisplayName("percent discount computes and rounds")
    void percentAmount() {
        DiscountCode code = code(DiscountType.PERCENT, "10", null);
        assertThat(discountService.computeAmount(code, new BigDecimal("899.99")))
                .isEqualByComparingTo("90.00");
    }

    @Test
    @DisplayName("percent discount honours the cap")
    void percentCap() {
        DiscountCode code = code(DiscountType.PERCENT, "10", "50.00");
        assertThat(discountService.computeAmount(code, new BigDecimal("900.00")))
                .isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("flat discount never exceeds the subtotal")
    void flatClampedToSubtotal() {
        DiscountCode code = code(DiscountType.FLAT, "500.00", null);
        assertThat(discountService.computeAmount(code, new BigDecimal("200.00")))
                .isEqualByComparingTo("200.00");
    }

    // ---- validation ----

    @Test
    @DisplayName("unknown code is rejected")
    void unknownCode() {
        when(discountCodeRepository.findByCodeIgnoreCase("NOPE")).thenReturn(Optional.empty());
        assertDiscountInvalid(() -> discountService.applyCode("nope", new BigDecimal("500"), 1L));
    }

    @Test
    @DisplayName("inactive code is rejected")
    void inactiveCode() {
        DiscountCode code = code(DiscountType.FLAT, "50", null);
        code.setActive(false);
        when(discountCodeRepository.findByCodeIgnoreCase(anyString())).thenReturn(Optional.of(code));
        assertDiscountInvalid(() -> discountService.applyCode("X", new BigDecimal("500"), 1L));
    }

    @Test
    @DisplayName("expired code is rejected")
    void expiredCode() {
        DiscountCode code = code(DiscountType.FLAT, "50", null);
        code.setValidUntil(LocalDateTime.of(2026, 7, 1, 0, 0));
        when(discountCodeRepository.findByCodeIgnoreCase(anyString())).thenReturn(Optional.of(code));
        assertDiscountInvalid(() -> discountService.applyCode("X", new BigDecimal("500"), 1L));
    }

    @Test
    @DisplayName("subtotal below the minimum order is rejected")
    void belowMinOrder() {
        DiscountCode code = code(DiscountType.FLAT, "50", null);
        code.setMinOrderAmount(new BigDecimal("300.00"));
        when(discountCodeRepository.findByCodeIgnoreCase(anyString())).thenReturn(Optional.of(code));
        assertDiscountInvalid(() -> discountService.applyCode("X", new BigDecimal("299.99"), 1L));
    }

    @Test
    @DisplayName("per-user limit counts existing bookings")
    void perUserLimit() {
        DiscountCode code = code(DiscountType.FLAT, "50", null);
        code.setPerUserLimit(1);
        when(discountCodeRepository.findByCodeIgnoreCase(anyString())).thenReturn(Optional.of(code));
        when(bookingRepository.countByUserIdAndDiscountCodeIgnoreCaseAndStatusIn(anyLong(), anyString(), any()))
                .thenReturn(1L);
        assertDiscountInvalid(() -> discountService.applyCode("X", new BigDecimal("500"), 1L));
    }

    @Test
    @DisplayName("exhausted total usage limit is rejected via the atomic consume")
    void totalLimitExhausted() {
        DiscountCode code = code(DiscountType.FLAT, "50", null);
        when(discountCodeRepository.findByCodeIgnoreCase(anyString())).thenReturn(Optional.of(code));
        when(discountCodeRepository.tryConsume(code.getId())).thenReturn(0);
        assertDiscountInvalid(() -> discountService.applyCode("X", new BigDecimal("500"), 1L));
    }

    @Test
    @DisplayName("valid code consumes one redemption and returns the amount")
    void happyPath() {
        DiscountCode code = code(DiscountType.PERCENT, "10", null);
        when(discountCodeRepository.findByCodeIgnoreCase(anyString())).thenReturn(Optional.of(code));
        when(discountCodeRepository.tryConsume(code.getId())).thenReturn(1);

        DiscountService.AppliedDiscount applied =
                discountService.applyCode("save10", new BigDecimal("900.00"), 1L);

        assertThat(applied.amount()).isEqualByComparingTo("90.00");
        assertThat(applied.code()).isSameAs(code);
    }

    private void assertDiscountInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.DISCOUNT_INVALID);
    }

    private DiscountCode code(DiscountType type, String value, String cap) {
        DiscountCode code = DiscountCode.builder()
                .code("SAVE10")
                .type(type)
                .value(new BigDecimal(value))
                .maxDiscountAmount(cap == null ? null : new BigDecimal(cap))
                .active(true)
                .build();
        code.setId(42L);
        return code;
    }
}
