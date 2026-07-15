package com.moviebooking.hold.dto;

import com.moviebooking.hold.SeatHold;
import com.moviebooking.hold.SeatHoldStatus;
import com.moviebooking.pricing.PriceQuote;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class HoldDtos {

    private HoldDtos() {
    }

    public record HoldRequest(
            @NotNull Long showId,
            @NotEmpty @Size(max = 20) List<@NotNull Long> seatIds
    ) {
    }

    public record HoldResponse(
            Long id,
            Long showId,
            SeatHoldStatus status,
            Instant expiresAt,
            long secondsRemaining,
            List<PriceQuote.SeatPriceLine> seats,
            BigDecimal subtotal,
            String currency
    ) {

        public static HoldResponse from(SeatHold hold, PriceQuote quote, String currency, Instant now) {
            long remaining = hold.getStatus() == SeatHoldStatus.ACTIVE
                    ? Math.max(0, hold.getExpiresAt().getEpochSecond() - now.getEpochSecond())
                    : 0;
            return new HoldResponse(hold.getId(), hold.getShow().getId(), hold.getStatus(),
                    hold.getExpiresAt(), remaining, quote.lines(), quote.subtotal(), currency);
        }
    }
}
