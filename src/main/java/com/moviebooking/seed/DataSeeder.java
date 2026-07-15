package com.moviebooking.seed;

import com.moviebooking.auth.Role;
import com.moviebooking.auth.User;
import com.moviebooking.auth.UserRepository;
import com.moviebooking.catalog.CatalogAdminService;
import com.moviebooking.catalog.SeatType;
import com.moviebooking.catalog.ShowAdminService;
import com.moviebooking.catalog.dto.CatalogDtos.CityRequest;
import com.moviebooking.catalog.dto.CatalogDtos.CityResponse;
import com.moviebooking.catalog.dto.CatalogDtos.MovieRequest;
import com.moviebooking.catalog.dto.CatalogDtos.MovieResponse;
import com.moviebooking.catalog.dto.CatalogDtos.ScreenRequest;
import com.moviebooking.catalog.dto.CatalogDtos.ScreenResponse;
import com.moviebooking.catalog.dto.CatalogDtos.SeatLayoutRequest;
import com.moviebooking.catalog.dto.CatalogDtos.SeatRowSpec;
import com.moviebooking.catalog.dto.CatalogDtos.TheaterRequest;
import com.moviebooking.catalog.dto.CatalogDtos.TheaterResponse;
import com.moviebooking.catalog.dto.ShowDtos.ShowCreateRequest;
import com.moviebooking.config.AppProperties;
import com.moviebooking.discount.DiscountService;
import com.moviebooking.discount.DiscountType;
import com.moviebooking.discount.dto.DiscountDtos.DiscountCodeRequest;
import com.moviebooking.refund.RefundPolicyService;
import com.moviebooking.refund.dto.RefundDtos.RefundPolicyRequest;
import com.moviebooking.refund.dto.RefundDtos.RefundPolicyResponse;
import com.moviebooking.refund.dto.RefundDtos.RefundRuleDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;

/**
 * Populates a fresh database with a demo catalog so the API is explorable
 * immediately. Disabled with app.seed.enabled=false (tests do this).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    public static final String ADMIN_EMAIL = "admin@moviebook.dev";
    public static final String ADMIN_PASSWORD = "Admin@123";
    public static final String CUSTOMER_EMAIL = "alice@example.com";
    public static final String CUSTOMER_PASSWORD = "Password@123";

    private final UserRepository userRepository;
    private final CatalogAdminService catalogAdminService;
    private final ShowAdminService showAdminService;
    private final DiscountService discountService;
    private final RefundPolicyService refundPolicyService;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;
    private final Clock clock;

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.info("Seed skipped — data already present");
            return;
        }
        seedUsers();
        RefundPolicyResponse standard = seedRefundPolicies();
        seedCatalog(standard);
        seedDiscounts();
        log.info("Seed complete. Admin: {} / {} — Customer: {} / {}",
                ADMIN_EMAIL, ADMIN_PASSWORD, CUSTOMER_EMAIL, CUSTOMER_PASSWORD);
    }

    private void seedUsers() {
        userRepository.save(User.builder()
                .fullName("System Admin")
                .email(ADMIN_EMAIL)
                .password(passwordEncoder.encode(ADMIN_PASSWORD))
                .role(Role.ADMIN)
                .createdAt(clock.instant())
                .build());
        userRepository.save(User.builder()
                .fullName("Alice Kumar")
                .email(CUSTOMER_EMAIL)
                .password(passwordEncoder.encode(CUSTOMER_PASSWORD))
                .role(Role.CUSTOMER)
                .createdAt(clock.instant())
                .build());
    }

    private RefundPolicyResponse seedRefundPolicies() {
        RefundPolicyResponse standard = refundPolicyService.create(new RefundPolicyRequest(
                "Standard", "Full refund 48h out, 75% within 48h, 50% within 24h, none within 2h",
                List.of(new RefundRuleDto(48, 100), new RefundRuleDto(24, 75), new RefundRuleDto(2, 50)),
                true));
        refundPolicyService.create(new RefundPolicyRequest(
                "Strict", "50% refund up to 24h before the show, nothing after",
                List.of(new RefundRuleDto(24, 50)),
                false));
        return standard;
    }

    private void seedCatalog(RefundPolicyResponse standardPolicy) {
        CityResponse mumbai = catalogAdminService.createCity(new CityRequest("Mumbai", "Maharashtra"));
        CityResponse bengaluru = catalogAdminService.createCity(new CityRequest("Bengaluru", "Karnataka"));

        TheaterResponse regal = catalogAdminService.createTheater(new TheaterRequest(
                "Regal Cinema", "Colaba Causeway", mumbai.id(), standardPolicy.id()));
        TheaterResponse pvrJuhu = catalogAdminService.createTheater(new TheaterRequest(
                "PVR Juhu", "Juhu Tara Road", mumbai.id(), null));
        TheaterResponse inoxGaruda = catalogAdminService.createTheater(new TheaterRequest(
                "INOX Garuda Mall", "Magrath Road", bengaluru.id(), standardPolicy.id()));

        SeatLayoutRequest standardLayout = new SeatLayoutRequest(List.of(
                new SeatRowSpec("A", 10, SeatType.REGULAR),
                new SeatRowSpec("B", 10, SeatType.REGULAR),
                new SeatRowSpec("C", 10, SeatType.REGULAR),
                new SeatRowSpec("D", 8, SeatType.PREMIUM),
                new SeatRowSpec("E", 8, SeatType.PREMIUM),
                new SeatRowSpec("F", 4, SeatType.RECLINER)));
        SeatLayoutRequest compactLayout = new SeatLayoutRequest(List.of(
                new SeatRowSpec("A", 8, SeatType.REGULAR),
                new SeatRowSpec("B", 8, SeatType.REGULAR),
                new SeatRowSpec("C", 6, SeatType.PREMIUM)));

        ScreenResponse regal1 = seedScreen(regal, "Screen 1", standardLayout);
        ScreenResponse regal2 = seedScreen(regal, "Screen 2", compactLayout);
        ScreenResponse pvr1 = seedScreen(pvrJuhu, "Audi 1", standardLayout);
        ScreenResponse inox1 = seedScreen(inoxGaruda, "Screen 1", compactLayout);

        MovieResponse interstellar = catalogAdminService.createMovie(new MovieRequest(
                "Interstellar", "English", "Sci-Fi", 169, "UA",
                "A team of explorers travel through a wormhole in space."));
        MovieResponse threeIdiots = catalogAdminService.createMovie(new MovieRequest(
                "3 Idiots", "Hindi", "Comedy/Drama", 170, "UA",
                "Two friends search for their long-lost college companion."));
        MovieResponse rrr = catalogAdminService.createMovie(new MovieRequest(
                "RRR", "Telugu", "Action", 187, "UA",
                "A fictitious story about two legendary revolutionaries."));

        Map<SeatType, BigDecimal> fullPrices = Map.of(
                SeatType.REGULAR, new BigDecimal("200.00"),
                SeatType.PREMIUM, new BigDecimal("350.00"),
                SeatType.RECLINER, new BigDecimal("600.00"));
        Map<SeatType, BigDecimal> compactPrices = Map.of(
                SeatType.REGULAR, new BigDecimal("180.00"),
                SeatType.PREMIUM, new BigDecimal("300.00"));

        LocalDate today = LocalDate.now(clock);
        LocalDate saturday = today.with(TemporalAdjusters.next(DayOfWeek.SATURDAY));

        // Weekday evening shows
        createShow(regal1, interstellar, today.plusDays(1).atTime(18, 30), fullPrices);
        createShow(regal1, threeIdiots, today.plusDays(1).atTime(22, 0), fullPrices);
        createShow(regal2, threeIdiots, today.plusDays(2).atTime(19, 0), compactPrices);
        createShow(pvr1, rrr, today.plusDays(1).atTime(20, 0), fullPrices);
        createShow(inox1, interstellar, today.plusDays(2).atTime(21, 0), compactPrices);
        // Weekend shows (weekend surcharge applies)
        createShow(regal1, rrr, saturday.atTime(LocalTime.of(20, 0)), fullPrices);
        createShow(pvr1, interstellar, saturday.atTime(LocalTime.of(17, 0)), fullPrices);
    }

    private ScreenResponse seedScreen(TheaterResponse theater, String name, SeatLayoutRequest layout) {
        ScreenResponse screen = catalogAdminService.createScreen(theater.id(), new ScreenRequest(name));
        catalogAdminService.replaceSeatLayout(screen.id(), layout);
        return screen;
    }

    private void createShow(ScreenResponse screen, MovieResponse movie, LocalDateTime start,
                            Map<SeatType, BigDecimal> prices) {
        // Guard: seeded times are always in the future relative to "now",
        // but re-runs close to midnight could drift — skip quietly if so.
        if (!start.isAfter(LocalDateTime.now(clock).plusHours(1))) {
            start = start.plusDays(7);
        }
        showAdminService.createShow(new ShowCreateRequest(screen.id(), movie.id(), start, prices));
    }

    private void seedDiscounts() {
        discountService.create(new DiscountCodeRequest(
                "WELCOME10", "10% off, capped at " + properties.currency() + " 100",
                DiscountType.PERCENT, new BigDecimal("10"), new BigDecimal("100.00"),
                null, null, null, null, 1));
        discountService.create(new DiscountCodeRequest(
                "FLAT50", "Flat 50 off on orders of 300+",
                DiscountType.FLAT, new BigDecimal("50.00"), null, new BigDecimal("300.00"),
                null, null, null, null));
        discountService.create(new DiscountCodeRequest(
                "LIMITED2", "Flat 100 off — only 2 redemptions system-wide (demo for usage limits)",
                DiscountType.FLAT, new BigDecimal("100.00"), null, null,
                null, null, 2, null));
    }
}
