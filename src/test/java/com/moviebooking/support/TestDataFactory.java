package com.moviebooking.support;

import com.moviebooking.auth.Role;
import com.moviebooking.auth.User;
import com.moviebooking.auth.UserRepository;
import com.moviebooking.catalog.City;
import com.moviebooking.catalog.CityRepository;
import com.moviebooking.catalog.Movie;
import com.moviebooking.catalog.MovieRepository;
import com.moviebooking.catalog.Screen;
import com.moviebooking.catalog.ScreenRepository;
import com.moviebooking.catalog.Seat;
import com.moviebooking.catalog.SeatRepository;
import com.moviebooking.catalog.SeatType;
import com.moviebooking.catalog.ShowAdminService;
import com.moviebooking.catalog.Theater;
import com.moviebooking.catalog.TheaterRepository;
import com.moviebooking.catalog.dto.ShowDtos.ShowCreateRequest;
import com.moviebooking.catalog.dto.ShowDtos.ShowResponse;
import com.moviebooking.discount.DiscountCode;
import com.moviebooking.discount.DiscountCodeRepository;
import com.moviebooking.discount.DiscountType;
import com.moviebooking.refund.RefundPolicy;
import com.moviebooking.refund.RefundPolicyRepository;
import com.moviebooking.refund.RefundRule;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Builds isolated catalog fixtures for integration tests. Every call uses a
 * unique suffix so tests sharing one application context never collide.
 */
@Component
public class TestDataFactory {

    public static final String PASSWORD = "Password@123";

    private static final AtomicInteger COUNTER = new AtomicInteger();

    private final UserRepository userRepository;
    private final CityRepository cityRepository;
    private final TheaterRepository theaterRepository;
    private final ScreenRepository screenRepository;
    private final SeatRepository seatRepository;
    private final MovieRepository movieRepository;
    private final RefundPolicyRepository refundPolicyRepository;
    private final DiscountCodeRepository discountCodeRepository;
    private final ShowAdminService showAdminService;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public TestDataFactory(UserRepository userRepository, CityRepository cityRepository,
                           TheaterRepository theaterRepository, ScreenRepository screenRepository,
                           SeatRepository seatRepository, MovieRepository movieRepository,
                           RefundPolicyRepository refundPolicyRepository,
                           DiscountCodeRepository discountCodeRepository,
                           ShowAdminService showAdminService, PasswordEncoder passwordEncoder, Clock clock) {
        this.userRepository = userRepository;
        this.cityRepository = cityRepository;
        this.theaterRepository = theaterRepository;
        this.screenRepository = screenRepository;
        this.seatRepository = seatRepository;
        this.movieRepository = movieRepository;
        this.refundPolicyRepository = refundPolicyRepository;
        this.discountCodeRepository = discountCodeRepository;
        this.showAdminService = showAdminService;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    public record CatalogBundle(City city, Theater theater, Screen screen, List<Seat> seats,
                                Movie movie, Long showId, ShowResponse show) {

        /** The five REGULAR seats come first, then two PREMIUM. */
        public Seat seat(int index) {
            return seats.get(index);
        }
    }

    public User customer() {
        return user(Role.CUSTOMER);
    }

    public User admin() {
        return user(Role.ADMIN);
    }

    private User user(Role role) {
        int n = COUNTER.incrementAndGet();
        return userRepository.save(User.builder()
                .fullName("Test " + role + " " + n)
                .email("user" + n + "@test.dev")
                .password(passwordEncoder.encode(PASSWORD))
                .role(role)
                .createdAt(clock.instant())
                .build());
    }

    /** Full catalog with a show starting {@code hoursFromNow} hours from now. */
    public CatalogBundle catalogWithShow(double hoursFromNow) {
        return catalogWithShow(hoursFromNow, null);
    }

    public CatalogBundle catalogWithShow(double hoursFromNow, RefundPolicy theaterPolicy) {
        int n = COUNTER.incrementAndGet();
        City city = cityRepository.save(City.builder().name("City-" + n).state("State").build());
        Theater theater = theaterRepository.save(Theater.builder()
                .name("Theater-" + n).address("Addr").city(city).refundPolicy(theaterPolicy).build());
        Screen screen = screenRepository.save(Screen.builder().name("Screen-" + n).theater(theater).build());
        List<Seat> seats = seatRepository.saveAll(List.of(
                seat(screen, "A", 1, SeatType.REGULAR),
                seat(screen, "A", 2, SeatType.REGULAR),
                seat(screen, "A", 3, SeatType.REGULAR),
                seat(screen, "A", 4, SeatType.REGULAR),
                seat(screen, "A", 5, SeatType.REGULAR),
                seat(screen, "B", 1, SeatType.PREMIUM),
                seat(screen, "B", 2, SeatType.PREMIUM)));
        Movie movie = movieRepository.save(Movie.builder()
                .title("Movie-" + n).language("English").genre("Drama")
                .durationMinutes(120).certification("UA").active(true).build());

        LocalDateTime start = LocalDateTime.now(clock).plusMinutes(Math.round(hoursFromNow * 60));
        ShowResponse show = showAdminService.createShow(new ShowCreateRequest(
                screen.getId(), movie.getId(), start,
                java.util.Map.of(SeatType.REGULAR, new BigDecimal("200.00"),
                        SeatType.PREMIUM, new BigDecimal("350.00"))));
        return new CatalogBundle(city, theater, screen, seats, movie, show.id(), show);
    }

    public RefundPolicy policy(boolean asDefault, RefundRule... rules) {
        int n = COUNTER.incrementAndGet();
        if (asDefault) {
            refundPolicyRepository.findByDefaultPolicyTrue().ifPresent(existing -> {
                existing.setDefaultPolicy(false);
                refundPolicyRepository.save(existing);
            });
        }
        return refundPolicyRepository.save(RefundPolicy.builder()
                .name("Policy-" + n)
                .active(true)
                .defaultPolicy(asDefault)
                .rules(List.of(rules))
                .build());
    }

    public DiscountCode discount(String prefix, DiscountType type, String value,
                                 Integer totalLimit, Integer perUserLimit) {
        int n = COUNTER.incrementAndGet();
        return discountCodeRepository.save(DiscountCode.builder()
                .code(prefix.toUpperCase() + n)
                .type(type)
                .value(new BigDecimal(value))
                .totalUsageLimit(totalLimit)
                .perUserLimit(perUserLimit)
                .active(true)
                .build());
    }

    private Seat seat(Screen screen, String row, int number, SeatType type) {
        return Seat.builder().screen(screen).rowLabel(row).seatNumber(number).type(type).build();
    }
}
