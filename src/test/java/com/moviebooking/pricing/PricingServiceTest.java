package com.moviebooking.pricing;

import com.moviebooking.catalog.Seat;
import com.moviebooking.catalog.SeatType;
import com.moviebooking.catalog.Show;
import com.moviebooking.common.error.ApiException;
import com.moviebooking.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PricingServiceTest {

    /** 2026-07-09 is a Thursday, 2026-07-11 a Saturday. */
    private static final LocalDateTime WEEKDAY = LocalDateTime.of(2026, 7, 9, 18, 0);
    private static final LocalDateTime SATURDAY = LocalDateTime.of(2026, 7, 11, 18, 0);

    private PricingService pricingService;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties("Asia/Kolkata", "INR", null, null,
                new AppProperties.Pricing(new BigDecimal("25")), null, null);
        pricingService = new PricingService(properties);
    }

    @Test
    @DisplayName("weekday show charges the plain per-tier base price")
    void weekdayPricing() {
        Show show = show(WEEKDAY, Map.of(
                SeatType.REGULAR, new BigDecimal("200.00"),
                SeatType.PREMIUM, new BigDecimal("350.00")));

        PriceQuote quote = pricingService.quote(show, List.of(
                seat(1L, "A", 1, SeatType.REGULAR),
                seat(2L, "B", 1, SeatType.PREMIUM)));

        assertThat(quote.subtotal()).isEqualByComparingTo("550.00");
        assertThat(quote.lines()).allSatisfy(line ->
                assertThat(line.weekendSurcharge()).isEqualByComparingTo("0.00"));
    }

    @Test
    @DisplayName("Saturday show adds the configured 25% weekend surcharge per seat")
    void weekendPricing() {
        Show show = show(SATURDAY, Map.of(SeatType.REGULAR, new BigDecimal("200.00")));

        PriceQuote quote = pricingService.quote(show, List.of(seat(1L, "A", 1, SeatType.REGULAR)));

        PriceQuote.SeatPriceLine line = quote.lines().get(0);
        assertThat(line.basePrice()).isEqualByComparingTo("200.00");
        assertThat(line.weekendSurcharge()).isEqualByComparingTo("50.00");
        assertThat(line.price()).isEqualByComparingTo("250.00");
        assertThat(quote.subtotal()).isEqualByComparingTo("250.00");
    }

    @Test
    @DisplayName("surcharge rounds half-up to two decimals")
    void surchargeRounding() {
        Show show = show(SATURDAY, Map.of(SeatType.REGULAR, new BigDecimal("199.99")));

        BigDecimal price = pricingService.effectivePrice(show, SeatType.REGULAR);

        // 199.99 * 0.25 = 49.9975 -> 50.00
        assertThat(price).isEqualByComparingTo("249.99");
    }

    @Test
    @DisplayName("a seat type with no configured price is rejected loudly")
    void missingPriceRejected() {
        Show show = show(WEEKDAY, Map.of(SeatType.REGULAR, new BigDecimal("200.00")));

        assertThatThrownBy(() -> pricingService.effectivePrice(show, SeatType.RECLINER))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("RECLINER");
    }

    private Show show(LocalDateTime start, Map<SeatType, BigDecimal> prices) {
        return Show.builder()
                .startTime(start)
                .endTime(start.plusMinutes(120))
                .basePrices(new java.util.EnumMap<>(prices))
                .build();
    }

    private Seat seat(Long id, String row, int number, SeatType type) {
        Seat seat = Seat.builder().rowLabel(row).seatNumber(number).type(type).build();
        seat.setId(id);
        return seat;
    }
}
