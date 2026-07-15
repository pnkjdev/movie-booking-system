package com.moviebooking.catalog;

import com.moviebooking.catalog.dto.CatalogDtos.CityRequest;
import com.moviebooking.catalog.dto.CatalogDtos.CityResponse;
import com.moviebooking.catalog.dto.CatalogDtos.MovieRequest;
import com.moviebooking.catalog.dto.CatalogDtos.MovieResponse;
import com.moviebooking.catalog.dto.CatalogDtos.ScreenRequest;
import com.moviebooking.catalog.dto.CatalogDtos.ScreenResponse;
import com.moviebooking.catalog.dto.CatalogDtos.SeatLayoutRequest;
import com.moviebooking.catalog.dto.CatalogDtos.SeatResponse;
import com.moviebooking.catalog.dto.CatalogDtos.TheaterRequest;
import com.moviebooking.catalog.dto.CatalogDtos.TheaterResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin - Catalog", description = "Manage cities, theaters, screens, seat layouts and movies")
public class AdminCatalogController {

    private final CatalogAdminService catalogAdminService;

    // ---- Cities ----

    @Operation(summary = "Create a city")
    @PostMapping("/cities")
    public ResponseEntity<CityResponse> createCity(@Valid @RequestBody CityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogAdminService.createCity(request));
    }

    @Operation(summary = "Update a city")
    @PutMapping("/cities/{id}")
    public CityResponse updateCity(@PathVariable Long id, @Valid @RequestBody CityRequest request) {
        return catalogAdminService.updateCity(id, request);
    }

    @Operation(summary = "Delete an empty city")
    @DeleteMapping("/cities/{id}")
    public ResponseEntity<Void> deleteCity(@PathVariable Long id) {
        catalogAdminService.deleteCity(id);
        return ResponseEntity.noContent().build();
    }

    // ---- Theaters ----

    @Operation(summary = "Create a theater in a city")
    @PostMapping("/theaters")
    public ResponseEntity<TheaterResponse> createTheater(@Valid @RequestBody TheaterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogAdminService.createTheater(request));
    }

    @Operation(summary = "Update a theater (name, address, city, refund policy)")
    @PutMapping("/theaters/{id}")
    public TheaterResponse updateTheater(@PathVariable Long id, @Valid @RequestBody TheaterRequest request) {
        return catalogAdminService.updateTheater(id, request);
    }

    // ---- Screens & seat layout ----

    @Operation(summary = "Add a screen (auditorium) to a theater")
    @PostMapping("/theaters/{theaterId}/screens")
    public ResponseEntity<ScreenResponse> createScreen(@PathVariable Long theaterId,
                                                       @Valid @RequestBody ScreenRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(catalogAdminService.createScreen(theaterId, request));
    }

    @Operation(summary = "Define or replace a screen's seat layout (blocked once shows exist)")
    @PutMapping("/screens/{screenId}/layout")
    public List<SeatResponse> replaceSeatLayout(@PathVariable Long screenId,
                                                @Valid @RequestBody SeatLayoutRequest request) {
        return catalogAdminService.replaceSeatLayout(screenId, request);
    }

    // ---- Movies ----

    @Operation(summary = "Create a movie")
    @PostMapping("/movies")
    public ResponseEntity<MovieResponse> createMovie(@Valid @RequestBody MovieRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogAdminService.createMovie(request));
    }

    @Operation(summary = "Update a movie")
    @PutMapping("/movies/{id}")
    public MovieResponse updateMovie(@PathVariable Long id, @Valid @RequestBody MovieRequest request) {
        return catalogAdminService.updateMovie(id, request);
    }

    @Operation(summary = "Deactivate a movie (soft delete)")
    @DeleteMapping("/movies/{id}")
    public MovieResponse deactivateMovie(@PathVariable Long id) {
        return catalogAdminService.deactivateMovie(id);
    }
}
