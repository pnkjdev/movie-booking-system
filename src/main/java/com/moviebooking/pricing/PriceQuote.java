package com.moviebooking.pricing;

import com.moviebooking.catalog.SeatType;

import java.math.BigDecimal;
import java.util.List;

/**
 * Priced view of a set of seats for one show, before any discount.
 */
public record PriceQuote(List<SeatPriceLine> lines, BigDecimal subtotal) {

    public record SeatPriceLine(
            Long seatId,
            String seatLabel,
            SeatType seatType,
            BigDecimal basePrice,
            BigDecimal weekendSurcharge,
            BigDecimal price
    ) {
    }
}
