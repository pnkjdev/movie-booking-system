package com.moviebooking.catalog.dto;

import com.moviebooking.catalog.City;
import com.moviebooking.catalog.Movie;
import com.moviebooking.catalog.Screen;
import com.moviebooking.catalog.Seat;
import com.moviebooking.catalog.SeatType;
import com.moviebooking.catalog.Theater;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class CatalogDtos {

    private CatalogDtos() {
    }

    // ---- Cities ----

    public record CityRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 100) String state
    ) {
    }

    public record CityResponse(Long id, String name, String state) {

        public static CityResponse from(City city) {
            return new CityResponse(city.getId(), city.getName(), city.getState());
        }
    }

    // ---- Theaters ----

    public record TheaterRequest(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 300) String address,
            @NotNull Long cityId,
            Long refundPolicyId
    ) {
    }

    public record TheaterResponse(Long id, String name, String address, CityResponse city, Long refundPolicyId) {

        public static TheaterResponse from(Theater theater) {
            return new TheaterResponse(theater.getId(), theater.getName(), theater.getAddress(),
                    CityResponse.from(theater.getCity()),
                    theater.getRefundPolicy() == null ? null : theater.getRefundPolicy().getId());
        }
    }

    // ---- Screens & seat layout ----

    public record ScreenRequest(@NotBlank @Size(max = 100) String name) {
    }

    public record ScreenResponse(Long id, String name, Long theaterId, long seatCount) {

        public static ScreenResponse from(Screen screen, long seatCount) {
            return new ScreenResponse(screen.getId(), screen.getName(), screen.getTheater().getId(), seatCount);
        }
    }

    public record SeatRowSpec(
            @NotBlank @Size(max = 5) String rowLabel,
            @Min(1) @Max(60) int seatCount,
            @NotNull SeatType type
    ) {
    }

    public record SeatLayoutRequest(@NotEmpty @Size(max = 40) List<@Valid SeatRowSpec> rows) {
    }

    public record SeatResponse(Long id, String rowLabel, int seatNumber, String label, SeatType type) {

        public static SeatResponse from(Seat seat) {
            return new SeatResponse(seat.getId(), seat.getRowLabel(), seat.getSeatNumber(),
                    seat.label(), seat.getType());
        }
    }

    // ---- Movies ----

    public record MovieRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 50) String language,
            @Size(max = 100) String genre,
            @NotNull @Positive @Max(600) Integer durationMinutes,
            @Size(max = 10) String certification,
            @Size(max = 1000) String description
    ) {
    }

    public record MovieResponse(Long id, String title, String language, String genre,
                                int durationMinutes, String certification, String description, boolean active) {

        public static MovieResponse from(Movie movie) {
            return new MovieResponse(movie.getId(), movie.getTitle(), movie.getLanguage(), movie.getGenre(),
                    movie.getDurationMinutes(), movie.getCertification(), movie.getDescription(), movie.isActive());
        }
    }
}
