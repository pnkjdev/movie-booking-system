package com.moviebooking.booking;

import com.moviebooking.auth.User;
import com.moviebooking.booking.dto.BookingDtos.BookingResponse;
import com.moviebooking.booking.dto.BookingDtos.CreateBookingRequest;
import com.moviebooking.booking.dto.BookingDtos.PaymentRequest;
import com.moviebooking.booking.dto.BookingDtos.PaymentResponse;
import com.moviebooking.catalog.ShowSeatStatus;
import com.moviebooking.common.error.ApiException;
import com.moviebooking.common.error.ErrorCode;
import com.moviebooking.discount.DiscountCode;
import com.moviebooking.discount.DiscountType;
import com.moviebooking.hold.SeatHold;
import com.moviebooking.hold.dto.HoldDtos.HoldResponse;
import com.moviebooking.notification.NotificationStatus;
import com.moviebooking.notification.NotificationType;
import com.moviebooking.support.IntegrationTestBase;
import com.moviebooking.support.TestDataFactory;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BookingFlowIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("hold -> book -> pay confirms the booking, books the seats and notifies asynchronously")
    void happyPath() {
        var bundle = factory.catalogWithShow(48);
        User user = factory.customer();

        HoldResponse hold = holdSeats(user, bundle, 0, 5); // A1 regular + B1 premium
        var expectedSubtotal = weekendAware(bundle, "550.00");
        assertThat(hold.subtotal()).isEqualByComparingTo(expectedSubtotal);

        BookingResponse booking = book(user, hold.id(), null);
        assertThat(booking.status()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(booking.totalAmount()).isEqualByComparingTo(expectedSubtotal);

        PaymentResponse payment = pay(user, booking.id());
        assertThat(payment.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.booking().status()).isEqualTo(BookingStatus.CONFIRMED);

        assertThat(showSeatRepository.findByShowIdWithSeat(bundle.showId()))
                .filteredOn(seat -> seat.getStatus() == ShowSeatStatus.BOOKED)
                .hasSize(2);

        // Confirmation is delivered off-thread; await the outbox drain.
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(notificationRepository.findByUserIdOrderByIdDesc(user.getId()))
                        .anySatisfy(notification -> {
                            assertThat(notification.getType()).isEqualTo(NotificationType.BOOKING_CONFIRMED);
                            assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
                        }));

        assertThat(bookingService.listMyBookings(user.getId()))
                .extracting(BookingResponse::reference)
                .contains(booking.reference());
    }

    @Test
    @DisplayName("a percent discount with cap reduces the total and records the redemption")
    void discountApplied() {
        var bundle = factory.catalogWithShow(48);
        User user = factory.customer();
        DiscountCode code = factory.discount("PCT", DiscountType.PERCENT, "10", null, null);

        HoldResponse hold = holdSeats(user, bundle, 0, 1, 5); // 200+200+350 on a weekday
        BookingResponse booking = book(user, hold.id(), code.getCode().toLowerCase());

        var expectedSubtotal = weekendAware(bundle, "750.00");
        var expectedDiscount = percentOf(expectedSubtotal, 10);
        assertThat(booking.subtotalAmount()).isEqualByComparingTo(expectedSubtotal);
        assertThat(booking.discountAmount()).isEqualByComparingTo(expectedDiscount);
        assertThat(booking.totalAmount()).isEqualByComparingTo(expectedSubtotal.subtract(expectedDiscount));
        assertThat(discountCodeRepository.findById(code.getId()).orElseThrow().getTimesUsed()).isEqualTo(1);
    }

    @Test
    @DisplayName("replaying an idempotency key returns the original payment without a second charge")
    void idempotentPayment() {
        var bundle = factory.catalogWithShow(48);
        User user = factory.customer();
        HoldResponse hold = holdSeats(user, bundle, 0);
        BookingResponse booking = book(user, hold.id(), null);

        String key = "idem-" + UUID.randomUUID();
        PaymentResponse first = bookingService.pay(user.getId(), booking.id(),
                new PaymentRequest(key, PaymentMethod.UPI, false));
        PaymentResponse replay = bookingService.pay(user.getId(), booking.id(),
                new PaymentRequest(key, PaymentMethod.UPI, false));

        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(replay.transactionId()).isEqualTo(first.transactionId());
        assertThat(paymentRepository.findByBookingIdOrderByCreatedAtDesc(booking.id())).hasSize(1);
    }

    @Test
    @DisplayName("a declined payment keeps the booking payable and the seats held")
    void declinedPayment() {
        var bundle = factory.catalogWithShow(48);
        User user = factory.customer();
        HoldResponse hold = holdSeats(user, bundle, 0);
        BookingResponse booking = book(user, hold.id(), null);

        PaymentResponse declined = bookingService.pay(user.getId(), booking.id(),
                new PaymentRequest("fail-" + UUID.randomUUID(), PaymentMethod.CARD, true));

        assertThat(declined.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(declined.booking().status()).isEqualTo(BookingStatus.PENDING_PAYMENT);

        // Retry succeeds while the hold is alive.
        PaymentResponse retry = pay(user, booking.id());
        assertThat(retry.booking().status()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    @DisplayName("payment after the hold expired is rejected and nothing is charged")
    void payAfterHoldExpiry() {
        var bundle = factory.catalogWithShow(48);
        User user = factory.customer();
        HoldResponse hold = holdSeats(user, bundle, 0);
        BookingResponse booking = book(user, hold.id(), null);

        SeatHold entity = seatHoldRepository.findById(hold.id()).orElseThrow();
        entity.setExpiresAt(Instant.now().minusSeconds(5));
        seatHoldRepository.save(entity);

        assertThatThrownBy(() -> pay(user, booking.id()))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.HOLD_EXPIRED);
        assertThat(paymentRepository.findByBookingIdOrderByCreatedAtDesc(booking.id())).isEmpty();
    }

    @Test
    @DisplayName("another user's hold cannot be booked")
    void foreignHoldRejected() {
        var bundle = factory.catalogWithShow(48);
        User owner = factory.customer();
        User attacker = factory.customer();
        HoldResponse hold = holdSeats(owner, bundle, 0);

        assertThatThrownBy(() -> book(attacker, hold.id(), null))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("one hold produces at most one booking")
    void duplicateBookingForHoldRejected() {
        var bundle = factory.catalogWithShow(48);
        User user = factory.customer();
        HoldResponse hold = holdSeats(user, bundle, 0);
        book(user, hold.id(), null);

        assertThatThrownBy(() -> book(user, hold.id(), null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("full journey over HTTP: register, hold, book, pay, history")
    void restLevelJourney() throws Exception {
        var bundle = factory.catalogWithShow(48);
        String email = "rest-journey-" + System.nanoTime() + "@test.dev";

        String registerBody = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Rest Journey", "email", email, "password", "Password@123"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(registerBody).get("token").asText();

        String holdBody = mockMvc.perform(post("/api/v1/holds")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "showId", bundle.showId(),
                                "seatIds", java.util.List.of(bundle.seat(2).getId())))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long holdId = objectMapper.readTree(holdBody).get("id").asLong();

        String bookingBody = mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("holdId", holdId))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long bookingId = objectMapper.readTree(bookingBody).get("id").asLong();

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "idempotencyKey", "rest-" + UUID.randomUUID(),
                                "method", "CARD"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.booking.status").value("CONFIRMED"));

        mockMvc.perform(get("/api/v1/bookings").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("CONFIRMED"));
    }
}
