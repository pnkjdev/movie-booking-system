package com.moviebooking.catalog.dto;

import com.moviebooking.catalog.SeatType;
import com.moviebooking.catalog.Show;
import com.moviebooking.catalog.ShowSeatStatus;
import com.moviebooking.catalog.ShowStatus;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class ShowDtos {

    private ShowDtos() {
    }

    public record ShowCreateRequest(
            @NotNull Long screenId,
            @NotNull Long movieId,
            @NotNull @Future LocalDateTime startTime,
            @NotEmpty Map<SeatType, @NotNull @Positive BigDecimal> prices
    ) {
    }

    public record ShowResponse(
            Long id,
            Long movieId,
            String movieTitle,
            Long theaterId,
            String theaterName,
            Long screenId,
            String screenName,
            Long cityId,
            String cityName,
            LocalDateTime startTime,
            LocalDateTime endTime,
            ShowStatus status,
            boolean weekend,
            Map<SeatType, BigDecimal> basePrices,
            Map<SeatType, BigDecimal> effectivePrices
    ) {

        public static ShowResponse from(Show show, Map<SeatType, BigDecimal> effectivePrices) {
            return new ShowResponse(
                    show.getId(),
                    show.getMovie().getId(),
                    show.getMovie().getTitle(),
                    show.getScreen().getTheater().getId(),
                    show.getScreen().getTheater().getName(),
                    show.getScreen().getId(),
                    show.getScreen().getName(),
                    show.getScreen().getTheater().getCity().getId(),
                    show.getScreen().getTheater().getCity().getName(),
                    show.getStartTime(),
                    show.getEndTime(),
                    show.getStatus(),
                    show.isOnWeekend(),
                    Map.copyOf(show.getBasePrices()),
                    effectivePrices);
        }
    }

    public record ShowAvailabilityResponse(long totalSeats, long availableSeats) {
    }

    public record ShowDetailResponse(ShowResponse show, ShowAvailabilityResponse availability) {
    }

    public record SeatMapEntry(
            Long seatId,
            String rowLabel,
            int seatNumber,
            String label,
            SeatType type,
            ShowSeatStatus status,
            BigDecimal price
    ) {
    }

    public record SeatMapResponse(Long showId, List<SeatMapEntry> seats) {
    }
}
