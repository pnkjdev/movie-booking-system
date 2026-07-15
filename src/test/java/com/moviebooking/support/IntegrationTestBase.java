package com.moviebooking.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moviebooking.auth.User;
import com.moviebooking.booking.BookingRepository;
import com.moviebooking.booking.BookingService;
import com.moviebooking.booking.PaymentMethod;
import com.moviebooking.booking.PaymentRepository;
import com.moviebooking.booking.ShowCancellationService;
import com.moviebooking.booking.dto.BookingDtos.BookingResponse;
import com.moviebooking.booking.dto.BookingDtos.CreateBookingRequest;
import com.moviebooking.booking.dto.BookingDtos.PaymentRequest;
import com.moviebooking.booking.dto.BookingDtos.PaymentResponse;
import com.moviebooking.catalog.ShowSeatRepository;
import com.moviebooking.discount.DiscountCodeRepository;
import com.moviebooking.hold.SeatHoldRepository;
import com.moviebooking.hold.SeatHoldService;
import com.moviebooking.hold.dto.HoldDtos.HoldRequest;
import com.moviebooking.hold.dto.HoldDtos.HoldResponse;
import com.moviebooking.notification.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
public abstract class IntegrationTestBase {

    @Autowired
    protected TestDataFactory factory;
    @Autowired
    protected SeatHoldService seatHoldService;
    @Autowired
    protected BookingService bookingService;
    @Autowired
    protected ShowCancellationService showCancellationService;
    @Autowired
    protected SeatHoldRepository seatHoldRepository;
    @Autowired
    protected ShowSeatRepository showSeatRepository;
    @Autowired
    protected BookingRepository bookingRepository;
    @Autowired
    protected PaymentRepository paymentRepository;
    @Autowired
    protected NotificationRepository notificationRepository;
    @Autowired
    protected DiscountCodeRepository discountCodeRepository;
    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected Clock clock;

    protected HoldResponse holdSeats(User user, TestDataFactory.CatalogBundle bundle, int... seatIndexes) {
        List<Long> seatIds = Arrays.stream(seatIndexes)
                .mapToObj(index -> bundle.seat(index).getId())
                .toList();
        return seatHoldService.createHold(user.getId(), new HoldRequest(bundle.showId(), seatIds));
    }

    protected BookingResponse book(User user, Long holdId, String discountCode) {
        return bookingService.createBooking(user.getId(), new CreateBookingRequest(holdId, discountCode));
    }

    protected PaymentResponse pay(User user, Long bookingId) {
        return bookingService.pay(user.getId(), bookingId,
                new PaymentRequest("key-" + UUID.randomUUID(), PaymentMethod.CARD, false));
    }

    /** hold -> book -> pay in one go; returns the confirmed booking. */
    protected BookingResponse bookAndPay(User user, TestDataFactory.CatalogBundle bundle, int... seatIndexes) {
        HoldResponse hold = holdSeats(user, bundle, seatIndexes);
        BookingResponse booking = book(user, hold.id(), null);
        return pay(user, booking.id()).booking();
    }

    /**
     * Fixture shows are scheduled relative to "now", so a +48h show can land
     * on a Saturday depending on the day the suite runs. Money expectations
     * must therefore be weekend-aware: this returns the weekday amount, or
     * that amount plus the 25% weekend surcharge when the bundle's show
     * falls on Sat/Sun. (Exact surcharge math is unit-tested with pinned
     * dates in PricingServiceTest.)
     */
    protected BigDecimal weekendAware(TestDataFactory.CatalogBundle bundle, String weekdayAmount) {
        BigDecimal amount = new BigDecimal(weekdayAmount);
        if (bundle.show().weekend()) {
            amount = amount.multiply(new BigDecimal("1.25"));
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    protected BigDecimal percentOf(BigDecimal amount, int percent) {
        return amount.multiply(BigDecimal.valueOf(percent))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
