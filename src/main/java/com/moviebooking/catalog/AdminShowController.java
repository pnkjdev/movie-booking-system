package com.moviebooking.catalog;

import com.moviebooking.booking.ShowCancellationService;
import com.moviebooking.catalog.dto.ShowDtos.ShowCreateRequest;
import com.moviebooking.catalog.dto.ShowDtos.ShowResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/shows")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin - Shows", description = "Schedule shows with per-seat-type pricing")
public class AdminShowController {

    private final ShowAdminService showAdminService;
    private final ShowCancellationService showCancellationService;

    @Operation(summary = "Schedule a show (creates per-seat inventory; rejects overlaps)")
    @PostMapping
    public ResponseEntity<ShowResponse> createShow(@Valid @RequestBody ShowCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(showAdminService.createShow(request));
    }

    @Operation(summary = "Get a show (admin view)")
    @GetMapping("/{id}")
    public ShowResponse getShow(@PathVariable Long id) {
        return showAdminService.toResponse(id);
    }

    @Operation(summary = "Cancel a show: all confirmed bookings refunded 100%, active holds released")
    @PostMapping("/{id}/cancel")
    public ShowCancellationService.ShowCancellationResult cancelShow(@PathVariable Long id) {
        return showCancellationService.cancelShow(id);
    }
}
