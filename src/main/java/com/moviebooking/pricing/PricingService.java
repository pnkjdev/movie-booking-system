package com.moviebooking.pricing;

import com.moviebooking.catalog.Seat;
import com.moviebooking.catalog.SeatType;
import com.moviebooking.catalog.Show;
import com.moviebooking.common.error.ApiException;
import com.moviebooking.config.AppProperties;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Computes per-seat prices: the show's per-seat-type base price plus a
 * configurable weekend surcharge for Saturday/Sunday shows.
 */
@Service
public class PricingService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final BigDecimal weekendSurchargePercent;

    public PricingService(AppProperties properties) {
        this.weekendSurchargePercent = properties.pricing().weekendSurchargePercent();
    }

    public PriceQuote quote(Show show, List<Seat> seats) {
        List<PriceQuote.SeatPriceLine> lines = new ArrayList<>(seats.size());
        BigDecimal subtotal = BigDecimal.ZERO;
        for (Seat seat : seats) {
            BigDecimal base = basePrice(show, seat.getType());
            BigDecimal surcharge = weekendSurcharge(show, base);
            BigDecimal price = base.add(surcharge);
            lines.add(new PriceQuote.SeatPriceLine(seat.getId(), seat.label(), seat.getType(),
                    base, surcharge, price));
            subtotal = subtotal.add(price);
        }
        return new PriceQuote(List.copyOf(lines), subtotal.setScale(2, RoundingMode.HALF_UP));
    }

    public BigDecimal effectivePrice(Show show, SeatType seatType) {
        BigDecimal base = basePrice(show, seatType);
        return base.add(weekendSurcharge(show, base));
    }

    private BigDecimal basePrice(Show show, SeatType seatType) {
        BigDecimal base = show.getBasePrices().get(seatType);
        if (base == null) {
            // Guarded at show-creation time; hitting this means the catalog is inconsistent.
            throw ApiException.badRequest("Show " + show.getId() + " has no price for seat type " + seatType);
        }
        return base.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal weekendSurcharge(Show show, BigDecimal base) {
        if (!show.isOnWeekend()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return base.multiply(weekendSurchargePercent)
                .divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }
}
