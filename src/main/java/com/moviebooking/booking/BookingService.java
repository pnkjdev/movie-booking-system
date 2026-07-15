package com.moviebooking.booking;

import com.moviebooking.auth.Role;
import com.moviebooking.booking.dto.BookingDtos.BookingResponse;
import com.moviebooking.booking.dto.BookingDtos.CreateBookingRequest;
import com.moviebooking.booking.dto.BookingDtos.PaymentRequest;
import com.moviebooking.booking.dto.BookingDtos.PaymentResponse;
import com.moviebooking.catalog.Seat;
import com.moviebooking.catalog.Show;
import com.moviebooking.catalog.ShowSeat;
import com.moviebooking.catalog.ShowSeatRepository;
import com.moviebooking.catalog.ShowStatus;
import com.moviebooking.common.error.ApiException;
import com.moviebooking.common.error.ErrorCode;
import com.moviebooking.config.AppProperties;
import com.moviebooking.discount.DiscountService;
import com.moviebooking.hold.SeatHold;
import com.moviebooking.hold.SeatHoldRepository;
import com.moviebooking.hold.SeatHoldStatus;
import com.moviebooking.notification.NotificationService;
import com.moviebooking.pricing.PriceQuote;
import com.moviebooking.pricing.PricingService;
import com.moviebooking.refund.RefundCalculator;
import com.moviebooking.refund.RefundPolicy;
import com.moviebooking.refund.RefundPolicyService;
import com.moviebooking.refund.RefundRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Converts active holds into bookings and processes payments. Lock ordering
 * note: every path that needs both locks takes the HOLD row lock before the
 * BOOKING row lock (the sweeper also starts from the hold), so payment
 * confirmation and hold expiry can never deadlock or double-decide.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private static final String REFERENCE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int REFERENCE_LENGTH = 8;

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final SeatHoldRepository seatHoldRepository;
    private final ShowSeatRepository showSeatRepository;
    private final PricingService pricingService;
    private final DiscountService discountService;
    private final PaymentGateway paymentGateway;
    private final NotificationService notificationService;
    private final RefundPolicyService refundPolicyService;
    private final RefundCalculator refundCalculator;
    private final AppProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    /**
     * Creates a PENDING_PAYMENT booking from an active hold, pricing the
     * seats and applying an optional discount code. Payment must complete
     * before the hold's TTL runs out.
     */
    @Transactional
    public BookingResponse createBooking(Long userId, CreateBookingRequest request) {
        SeatHold hold = seatHoldRepository.lockById(request.holdId())
                .orElseThrow(() -> ApiException.notFound("Hold not found: " + request.holdId()));
        if (!hold.getUser().getId().equals(userId)) {
            throw ApiException.forbidden("This hold belongs to another user");
        }
        Instant now = clock.instant();
        if (hold.getStatus() != SeatHoldStatus.ACTIVE) {
            throw ApiException.conflict(ErrorCode.HOLD_INVALID,
                    "Hold is not active (current status: " + hold.getStatus() + ")");
        }
        if (hold.isExpired(now)) {
            throw ApiException.conflict(ErrorCode.HOLD_EXPIRED,
                    "The hold expired before checkout; please select seats again");
        }
        if (bookingRepository.findByHoldId(hold.getId()).isPresent()) {
            throw ApiException.conflict(ErrorCode.CONFLICT, "A booking already exists for this hold");
        }

        Show show = hold.getShow();
        if (show.getStatus() != ShowStatus.SCHEDULED
                || !show.getStartTime().isAfter(LocalDateTime.now(clock))) {
            throw ApiException.conflict(ErrorCode.SHOW_NOT_BOOKABLE, "The show is no longer bookable");
        }

        List<ShowSeat> heldSeats = showSeatRepository.findByHoldId(hold.getId());
        if (heldSeats.isEmpty()) {
            throw ApiException.conflict(ErrorCode.HOLD_INVALID, "The hold has no seats attached");
        }
        List<Seat> seats = heldSeats.stream().map(ShowSeat::getSeat).toList();
        PriceQuote quote = pricingService.quote(show, seats);

        BigDecimal discountAmount = BigDecimal.ZERO.setScale(2);
        String appliedCode = null;
        if (request.discountCode() != null && !request.discountCode().isBlank()) {
            DiscountService.AppliedDiscount applied =
                    discountService.applyCode(request.discountCode(), quote.subtotal(), userId);
            discountAmount = applied.amount();
            appliedCode = applied.code().getCode();
        }

        Booking booking = Booking.builder()
                .reference(newReference())
                .user(hold.getUser())
                .show(show)
                .hold(hold)
                .status(BookingStatus.PENDING_PAYMENT)
                .subtotalAmount(quote.subtotal())
                .discountAmount(discountAmount)
                .totalAmount(quote.subtotal().subtract(discountAmount))
                .discountCode(appliedCode)
                .currency(properties.currency())
                .createdAt(now)
                .build();
        for (PriceQuote.SeatPriceLine line : quote.lines()) {
            booking.addSeat(BookingSeat.builder()
                    .seatId(line.seatId())
                    .seatLabel(line.seatLabel())
                    .seatType(line.seatType())
                    .basePrice(line.basePrice())
                    .weekendSurcharge(line.weekendSurcharge())
                    .price(line.price())
                    .build());
        }
        booking = bookingRepository.save(booking);
        log.info("Created booking {} ({}) from hold {} — total {} {}",
                booking.getId(), booking.getReference(), hold.getId(),
                booking.getCurrency(), booking.getTotalAmount());
        return BookingResponse.from(booking);
    }

    /**
     * Idempotent payment: replaying an idempotency key returns the original
     * outcome without charging again. On success the hold is consumed and
     * the seats flip HELD -> BOOKED under their row locks. On a gateway
     * decline the attempt is recorded and the booking stays payable until
     * the hold expires.
     */
    @Transactional
    public PaymentResponse pay(Long userId, Long bookingId, PaymentRequest request) {
        Optional<Payment> replay = paymentRepository.findByIdempotencyKey(request.idempotencyKey());
        if (replay.isPresent()) {
            Payment payment = replay.get();
            if (!payment.getBooking().getId().equals(bookingId)) {
                throw ApiException.conflict(ErrorCode.CONFLICT,
                        "This idempotency key was already used for a different booking");
            }
            requireOwnerOrAdmin(payment.getBooking(), userId, null);
            return PaymentResponse.from(payment, payment.getBooking());
        }

        // Resolve the hold id first, then lock hold -> booking (same order
        // as the expiry sweeper) to keep the lock graph acyclic.
        Booking peek = bookingRepository.findById(bookingId)
                .orElseThrow(() -> ApiException.notFound("Booking not found: " + bookingId));
        if (!peek.getUser().getId().equals(userId)) {
            throw ApiException.forbidden("This booking belongs to another user");
        }
        Long holdId = peek.getHold().getId();
        SeatHold hold = seatHoldRepository.lockById(holdId)
                .orElseThrow(() -> ApiException.notFound("Hold not found for booking"));
        Booking booking = bookingRepository.lockById(bookingId).orElseThrow();

        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            throw ApiException.conflict(ErrorCode.CONFLICT, "Booking is already paid");
        }
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw ApiException.conflict(ErrorCode.CONFLICT,
                    "Booking cannot be paid (current status: " + booking.getStatus() + ")");
        }
        if (!hold.isUsable(clock.instant())) {
            throw ApiException.conflict(ErrorCode.HOLD_EXPIRED,
                    "The seat hold expired before payment completed; please start again");
        }

        PaymentGateway.ChargeResult result = paymentGateway.charge(new PaymentGateway.ChargeCommand(
                booking.getTotalAmount(), booking.getCurrency(), request.method(), request.simulateFailure()));

        Payment payment = paymentRepository.save(Payment.builder()
                .booking(booking)
                .idempotencyKey(request.idempotencyKey())
                .amount(booking.getTotalAmount())
                .method(request.method())
                .status(result.success() ? PaymentStatus.SUCCEEDED : PaymentStatus.FAILED)
                .transactionId(result.transactionId())
                .failureReason(result.failureReason())
                .createdAt(clock.instant())
                .build());

        if (result.success()) {
            confirm(booking, hold);
        } else {
            log.info("Payment declined for booking {}: {}", booking.getReference(), result.failureReason());
            notificationService.paymentFailed(booking, result.failureReason());
        }
        return PaymentResponse.from(payment, booking);
    }

    private void confirm(Booking booking, SeatHold hold) {
        List<ShowSeat> seats = showSeatRepository.lockByHoldId(hold.getId());
        seats.forEach(ShowSeat::markBooked);
        hold.setStatus(SeatHoldStatus.CONFIRMED);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setConfirmedAt(clock.instant());
        notificationService.bookingConfirmed(booking);
        log.info("Confirmed booking {} — {} seat(s) booked", booking.getReference(), seats.size());
    }

    /**
     * Cancels a CONFIRMED booking before showtime. The refund percentage is
     * decided by the theater's refund policy (or the system default): the
     * most generous satisfied "cancel >= N hours before" rule wins. Seats
     * are returned to inventory and become sellable again.
     */
    @Transactional
    public BookingResponse cancelBooking(Long userId, Role role, Long bookingId) {
        Booking booking = bookingRepository.lockById(bookingId)
                .orElseThrow(() -> ApiException.notFound("Booking not found: " + bookingId));
        requireOwnerOrAdmin(booking, userId, role);
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw ApiException.conflict(ErrorCode.BOOKING_NOT_CANCELLABLE,
                    "Only confirmed bookings can be cancelled (current status: " + booking.getStatus() + ")");
        }
        Show show = booking.getShow();
        LocalDateTime now = LocalDateTime.now(clock);
        if (!show.getStartTime().isAfter(now)) {
            throw ApiException.conflict(ErrorCode.BOOKING_NOT_CANCELLABLE,
                    "The show has already started; this booking can no longer be cancelled");
        }

        double hoursBeforeShow = java.time.Duration.between(now, show.getStartTime()).toMinutes() / 60.0;
        RefundCalculator.RefundOutcome outcome = refundCalculator.compute(
                resolveRefundRules(booking), booking.getTotalAmount(), hoursBeforeShow);

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(clock.instant());
        booking.setRefundPercent(outcome.percent());
        booking.setRefundAmount(outcome.amount());

        List<Long> seatIds = booking.getSeats().stream().map(BookingSeat::getSeatId).toList();
        List<ShowSeat> seats = showSeatRepository.lockByShowIdAndSeatIds(show.getId(), seatIds);
        seats.forEach(ShowSeat::release);

        notificationService.bookingCancelled(booking, outcome.amount());
        log.info("Cancelled booking {} — refund {}% = {} {} ({} seat(s) released)",
                booking.getReference(), outcome.percent(), booking.getCurrency(),
                outcome.amount(), seats.size());
        return BookingResponse.from(booking);
    }

    private List<RefundRule> resolveRefundRules(Booking booking) {
        RefundPolicy policy = booking.getShow().getScreen().getTheater().getRefundPolicy();
        if (policy == null || !policy.isActive()) {
            policy = refundPolicyService.defaultPolicy()
                    .filter(RefundPolicy::isActive)
                    .orElse(null);
        }
        return policy == null ? List.of() : policy.getRules();
    }

    @Transactional(readOnly = true)
    public BookingResponse getBooking(Long userId, Role role, Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> ApiException.notFound("Booking not found: " + bookingId));
        requireOwnerOrAdmin(booking, userId, role);
        return BookingResponse.from(booking);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> listMyBookings(Long userId) {
        return bookingRepository.findAllByUserIdWithDetails(userId).stream()
                .map(BookingResponse::from)
                .toList();
    }

    private void requireOwnerOrAdmin(Booking booking, Long userId, Role role) {
        if (role == Role.ADMIN) {
            return;
        }
        if (!booking.getUser().getId().equals(userId)) {
            throw ApiException.forbidden("This booking belongs to another user");
        }
    }

    private String newReference() {
        for (int attempt = 0; attempt < 5; attempt++) {
            StringBuilder builder = new StringBuilder("BK-");
            for (int i = 0; i < REFERENCE_LENGTH; i++) {
                builder.append(REFERENCE_ALPHABET.charAt(random.nextInt(REFERENCE_ALPHABET.length())));
            }
            String candidate = builder.toString();
            if (bookingRepository.findByReference(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique booking reference");
    }
}
