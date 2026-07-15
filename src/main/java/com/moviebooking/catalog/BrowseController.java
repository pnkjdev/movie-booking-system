package com.moviebooking.catalog;

import com.moviebooking.catalog.dto.CatalogDtos.CityResponse;
import com.moviebooking.catalog.dto.CatalogDtos.MovieResponse;
import com.moviebooking.catalog.dto.CatalogDtos.TheaterResponse;
import com.moviebooking.catalog.dto.ShowDtos.SeatMapResponse;
import com.moviebooking.catalog.dto.ShowDtos.ShowDetailResponse;
import com.moviebooking.catalog.dto.ShowDtos.ShowResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Browse", description = "Public catalog browsing — no login required")
public class BrowseController {

    private final BrowseService browseService;

    @Operation(summary = "List all cities")
    @GetMapping("/cities")
    public List<CityResponse> listCities() {
        return browseService.listCities();
    }

    @Operation(summary = "List theaters in a city")
    @GetMapping("/cities/{cityId}/theaters")
    public List<TheaterResponse> listTheaters(@PathVariable Long cityId) {
        return browseService.listTheaters(cityId);
    }

    @Operation(summary = "List active movies")
    @GetMapping("/movies")
    public List<MovieResponse> listMovies() {
        return browseService.listMovies();
    }

    @Operation(summary = "Search upcoming shows by city / movie / theater / date")
    @GetMapping("/shows")
    public List<ShowResponse> searchShows(
            @RequestParam(required = false) Long cityId,
            @RequestParam(required = false) Long movieId,
            @RequestParam(required = false) Long theaterId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return browseService.searchShows(cityId, movieId, theaterId, date);
    }

    @Operation(summary = "Show details with availability summary")
    @GetMapping("/shows/{showId}")
    public ShowDetailResponse showDetails(@PathVariable Long showId) {
        return browseService.showDetails(showId);
    }

    @Operation(summary = "Live seat map with per-seat status and price")
    @GetMapping("/shows/{showId}/seats")
    public SeatMapResponse seatMap(@PathVariable Long showId) {
        return browseService.seatMap(showId);
    }
}
