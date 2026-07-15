package com.moviebooking.hold;

import com.moviebooking.auth.security.UserPrincipal;
import com.moviebooking.hold.dto.HoldDtos.HoldRequest;
import com.moviebooking.hold.dto.HoldDtos.HoldResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/holds")
@RequiredArgsConstructor
@Tag(name = "Seat Holds", description = "Time-bound seat reservations that precede payment")
public class HoldController {

    private final SeatHoldService seatHoldService;

    @Operation(summary = "Hold seats for a show (expires automatically after the configured TTL)")
    @PostMapping
    public ResponseEntity<HoldResponse> createHold(@AuthenticationPrincipal UserPrincipal principal,
                                                   @Valid @RequestBody HoldRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(seatHoldService.createHold(principal.id(), request));
    }

    @Operation(summary = "Inspect one of your holds")
    @GetMapping("/{holdId}")
    public HoldResponse getHold(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long holdId) {
        return seatHoldService.getHold(principal.id(), holdId);
    }

    @Operation(summary = "Release a hold early, returning its seats to inventory")
    @DeleteMapping("/{holdId}")
    public ResponseEntity<Void> releaseHold(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable Long holdId) {
        seatHoldService.releaseHold(principal.id(), holdId);
        return ResponseEntity.noContent().build();
    }
}
