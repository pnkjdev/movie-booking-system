package com.moviebooking.booking.dto;

import com.moviebooking.booking.Booking;
import com.moviebooking.booking.BookingSeat;
import com.moviebooking.booking.BookingStatus;
import com.moviebooking.booking.Payment;
import com.moviebooking.booking.PaymentMethod;
import com.moviebooking.booking.PaymentStatus;
import com.moviebooking.catalog.SeatType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public final class BookingDtos {

    private BookingDtos() {
    }

    public record CreateBookingRequest(
            @NotNull Long holdId,
            @Size(max = 40) String discountCode
    ) {
    }

    public record PaymentRequest(
            @NotBlank @Size(max = 80) String idempotencyKey,
            @NotNull PaymentMethod method,
            boolean simulateFailure
    ) {
    }

    public record BookedSeatResponse(
            String seatLabel,
            SeatType seatType,
            BigDecimal basePrice,
            BigDecimal weekendSurcharge,
            BigDecimal price
    ) {

        public static BookedSeatResponse from(BookingSeat seat) {
            return new BookedSeatResponse(seat.getSeatLabel(), seat.getSeatType(),
                    seat.getBasePrice(), seat.getWeekendSurcharge(), seat.getPrice());
        }
    }

    public record BookingResponse(
            Long id,
            String reference,
            BookingStatus status,
            Long showId,
            String movieTitle,
            String theaterName,
            String screenName,
            String cityName,
            LocalDateTime showStartTime,
            List<BookedSeatResponse> seats,
            BigDecimal subtotalAmount,
            String discountCode,
            BigDecimal discountAmount,
            BigDecimal totalAmount,
            String currency,
            Instant createdAt,
            Instant confirmedAt,
            Instant cancelledAt,
            BigDecimal refundAmount,
            Integer refundPercent
    ) {

        public static BookingResponse from(Booking booking) {
            return new BookingResponse(
                    booking.getId(),
                    booking.getReference(),
                    booking.getStatus(),
                    booking.getShow().getId(),
                    booking.getShow().getMovie().getTitle(),
                    booking.getShow().getScreen().getTheater().getName(),
                    booking.getShow().getScreen().getName(),
                    booking.getShow().getScreen().getTheater().getCity().getName(),
                    booking.getShow().getStartTime(),
                    booking.getSeats().stream().map(BookedSeatResponse::from).toList(),
                    booking.getSubtotalAmount(),
                    booking.getDiscountCode(),
                    booking.getDiscountAmount(),
                    booking.getTotalAmount(),
                    booking.getCurrency(),
                    booking.getCreatedAt(),
                    booking.getConfirmedAt(),
                    booking.getCancelledAt(),
                    booking.getRefundAmount(),
                    booking.getRefundPercent());
        }
    }

    public record PaymentResponse(
            Long id,
            Long bookingId,
            PaymentStatus status,
            BigDecimal amount,
            PaymentMethod method,
            String transactionId,
            String failureReason,
            Instant createdAt,
            BookingResponse booking
    ) {

        public static PaymentResponse from(Payment payment, Booking booking) {
            return new PaymentResponse(payment.getId(), booking.getId(), payment.getStatus(),
                    payment.getAmount(), payment.getMethod(), payment.getTransactionId(),
                    payment.getFailureReason(), payment.getCreatedAt(), BookingResponse.from(booking));
        }
    }
}
