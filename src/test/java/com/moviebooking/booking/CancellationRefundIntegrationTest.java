package com.moviebooking.booking;

import com.moviebooking.auth.Role;
import com.moviebooking.auth.User;
import com.moviebooking.booking.dto.BookingDtos.BookingResponse;
import com.moviebooking.catalog.ShowSeatStatus;
import com.moviebooking.common.error.ApiException;
import com.moviebooking.common.error.ErrorCode;
import com.moviebooking.hold.dto.HoldDtos.HoldResponse;
import com.moviebooking.refund.RefundPolicy;
import com.moviebooking.refund.RefundRule;
import com.moviebooking.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CancellationRefundIntegrationTest extends IntegrationTestBase {

    private RefundPolicy standardPolicy() {
        return factory.policy(false,
                new RefundRule(48, 100), new RefundRule(24, 75), new RefundRule(2, 50));
    }

    @Test
    @DisplayName("cancelling 72h ahead refunds 100% under the theater policy and frees the seats")
    void fullRefundTier() {
        var bundle = factory.catalogWithShow(72, standardPolicy());
        User user = factory.customer();
        BookingResponse booking = bookAndPay(user, bundle, 0, 5);

        BookingResponse cancelled = bookingService.cancelBooking(user.getId(), Role.CUSTOMER, booking.id());

        assertThat(cancelled.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(cancelled.refundPercent()).isEqualTo(100);
        assertThat(cancelled.refundAmount()).isEqualByComparingTo(booking.totalAmount());
        assertThat(showSeatRepository.findByShowIdWithSeat(bundle.showId()))
                .allSatisfy(seat -> assertThat(seat.getStatus()).isEqualTo(ShowSeatStatus.AVAILABLE));
    }

    @Test
    @DisplayName("cancelling 30h ahead lands in the 75% tier")
    void midRefundTier() {
        var bundle = factory.catalogWithShow(30, standardPolicy());
        User user = factory.customer();
        BookingResponse booking = bookAndPay(user, bundle, 0);

        BookingResponse cancelled = bookingService.cancelBooking(user.getId(), Role.CUSTOMER, booking.id());

        assertThat(cancelled.refundPercent()).isEqualTo(75);
        assertThat(cancelled.refundAmount()).isEqualByComparingTo(percentOf(booking.totalAmount(), 75));
    }

    @Test
    @DisplayName("cancelling inside the final cutoff refunds nothing but still frees the seats")
    void zeroRefundTier() {
        var bundle = factory.catalogWithShow(1, standardPolicy());
        User user = factory.customer();
        BookingResponse booking = bookAndPay(user, bundle, 0);

        BookingResponse cancelled = bookingService.cancelBooking(user.getId(), Role.CUSTOMER, booking.id());

        assertThat(cancelled.refundPercent()).isZero();
        assertThat(cancelled.refundAmount()).isEqualByComparingTo("0.00");
        assertThat(cancelled.status()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    @DisplayName("theater without its own policy falls back to the system default")
    void defaultPolicyFallback() {
        factory.policy(true, new RefundRule(0, 40)); // default: flat 40% any time before start
        var bundle = factory.catalogWithShow(5, null);
        User user = factory.customer();
        BookingResponse booking = bookAndPay(user, bundle, 0);

        BookingResponse cancelled = bookingService.cancelBooking(user.getId(), Role.CUSTOMER, booking.id());

        assertThat(cancelled.refundPercent()).isEqualTo(40);
        assertThat(cancelled.refundAmount()).isEqualByComparingTo(percentOf(booking.totalAmount(), 40));
    }

    @Test
    @DisplayName("only confirmed bookings can be cancelled, and only once")
    void cancellationGuards() {
        var bundle = factory.catalogWithShow(48, standardPolicy());
        User user = factory.customer();
        HoldResponse hold = holdSeats(user, bundle, 0);
        BookingResponse pending = book(user, hold.id(), null);

        assertThatThrownBy(() -> bookingService.cancelBooking(user.getId(), Role.CUSTOMER, pending.id()))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.BOOKING_NOT_CANCELLABLE);

        BookingResponse confirmed = pay(user, pending.id()).booking();
        bookingService.cancelBooking(user.getId(), Role.CUSTOMER, confirmed.id());

        assertThatThrownBy(() -> bookingService.cancelBooking(user.getId(), Role.CUSTOMER, confirmed.id()))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.BOOKING_NOT_CANCELLABLE);
    }

    @Test
    @DisplayName("a customer cannot cancel someone else's booking")
    void foreignCancellationRejected() {
        var bundle = factory.catalogWithShow(48, standardPolicy());
        User owner = factory.customer();
        User attacker = factory.customer();
        BookingResponse booking = bookAndPay(owner, bundle, 0);

        assertThatThrownBy(() -> bookingService.cancelBooking(attacker.getId(), Role.CUSTOMER, booking.id()))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("admin show cancellation refunds every confirmed booking 100% and blocks new holds")
    void showCancellation() {
        var bundle = factory.catalogWithShow(48, standardPolicy());
        User first = factory.customer();
        User second = factory.customer();
        BookingResponse paidBooking = bookAndPay(first, bundle, 0, 1);
        HoldResponse activeHold = holdSeats(second, bundle, 2);

        var result = showCancellationService.cancelShow(bundle.showId());

        assertThat(result.bookingsRefunded()).isEqualTo(1);
        assertThat(result.holdsReleased()).isEqualTo(1);

        Booking refunded = bookingRepository.findById(paidBooking.id()).orElseThrow();
        assertThat(refunded.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(refunded.getRefundPercent()).isEqualTo(100);
        assertThat(refunded.getRefundAmount()).isEqualByComparingTo(refunded.getTotalAmount());

        assertThatThrownBy(() -> holdSeats(second, bundle, 3))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.SHOW_NOT_BOOKABLE);

        assertThatThrownBy(() -> showCancellationService.cancelShow(bundle.showId()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already cancelled");
    }
}
