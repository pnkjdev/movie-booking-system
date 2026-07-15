package com.moviebooking.booking;

import com.moviebooking.auth.security.UserPrincipal;
import com.moviebooking.booking.dto.BookingDtos.BookingResponse;
import com.moviebooking.booking.dto.BookingDtos.CreateBookingRequest;
import com.moviebooking.booking.dto.BookingDtos.PaymentRequest;
import com.moviebooking.booking.dto.BookingDtos.PaymentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
@Tag(name = "Bookings", description = "Create bookings from holds, pay, cancel, and view history")
public class BookingController {

    private final BookingService bookingService;

    @Operation(summary = "Create a booking from an active hold (optionally applying a discount code)")
    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(@AuthenticationPrincipal UserPrincipal principal,
                                                         @Valid @RequestBody CreateBookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bookingService.createBooking(principal.id(), request));
    }

    @Operation(summary = "Pay for a pending booking (idempotent by idempotencyKey)")
    @PostMapping("/{bookingId}/payments")
    public PaymentResponse pay(@AuthenticationPrincipal UserPrincipal principal,
                               @PathVariable Long bookingId,
                               @Valid @RequestBody PaymentRequest request) {
        return bookingService.pay(principal.id(), bookingId, request);
    }

    @Operation(summary = "Cancel a confirmed booking; refund follows the applicable refund policy")
    @PostMapping("/{bookingId}/cancel")
    public BookingResponse cancel(@AuthenticationPrincipal UserPrincipal principal,
                                  @PathVariable Long bookingId) {
        return bookingService.cancelBooking(principal.id(), principal.role(), bookingId);
    }

    @Operation(summary = "Get one of my bookings (admins can view any)")
    @GetMapping("/{bookingId}")
    public BookingResponse getBooking(@AuthenticationPrincipal UserPrincipal principal,
                                      @PathVariable Long bookingId) {
        return bookingService.getBooking(principal.id(), principal.role(), bookingId);
    }

    @Operation(summary = "My booking history (newest first)")
    @GetMapping
    public List<BookingResponse> myBookings(@AuthenticationPrincipal UserPrincipal principal) {
        return bookingService.listMyBookings(principal.id());
    }
}
