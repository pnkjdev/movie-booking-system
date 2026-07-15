package com.moviebooking.catalog;

import com.moviebooking.catalog.dto.CatalogDtos.CityResponse;
import com.moviebooking.catalog.dto.CatalogDtos.MovieResponse;
import com.moviebooking.catalog.dto.CatalogDtos.TheaterResponse;
import com.moviebooking.catalog.dto.ShowDtos.SeatMapEntry;
import com.moviebooking.catalog.dto.ShowDtos.SeatMapResponse;
import com.moviebooking.catalog.dto.ShowDtos.ShowAvailabilityResponse;
import com.moviebooking.catalog.dto.ShowDtos.ShowDetailResponse;
import com.moviebooking.catalog.dto.ShowDtos.ShowResponse;
import com.moviebooking.common.error.ApiException;
import com.moviebooking.pricing.PricingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BrowseService {

    private static final int DEFAULT_SEARCH_DAYS = 7;

    private final CityRepository cityRepository;
    private final TheaterRepository theaterRepository;
    private final MovieRepository movieRepository;
    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;
    private final PricingService pricingService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<CityResponse> listCities() {
        return cityRepository.findAll().stream()
                .sorted(Comparator.comparing(City::getName))
                .map(CityResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TheaterResponse> listTheaters(Long cityId) {
        if (!cityRepository.existsById(cityId)) {
            throw ApiException.notFound("City not found: " + cityId);
        }
        return theaterRepository.findByCityIdOrderByName(cityId).stream()
                .map(TheaterResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MovieResponse> listMovies() {
        return movieRepository.findByActiveTrueOrderByTitle().stream()
                .map(MovieResponse::from)
                .toList();
    }

    /**
     * Shows for a specific date, or the next {@value DEFAULT_SEARCH_DAYS}
     * days when no date is given. Shows that already started are excluded.
     */
    @Transactional(readOnly = true)
    public List<ShowResponse> searchShows(Long cityId, Long movieId, Long theaterId, LocalDate date) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime from;
        LocalDateTime to;
        if (date == null) {
            from = now;
            to = now.plusDays(DEFAULT_SEARCH_DAYS);
        } else {
            from = date.atStartOfDay().isBefore(now) && date.equals(LocalDate.now(clock))
                    ? now : date.atStartOfDay();
            to = date.plusDays(1).atStartOfDay();
        }
        return showRepository.search(ShowStatus.SCHEDULED, cityId, movieId, theaterId, from, to).stream()
                .map(this::toShowResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ShowDetailResponse showDetails(Long showId) {
        Show show = showRepository.findByIdWithDetails(showId)
                .orElseThrow(() -> ApiException.notFound("Show not found: " + showId));
        long total = showSeatRepository.countByShowId(showId);
        long available = showSeatRepository.findByShowIdWithSeat(showId).stream()
                .filter(seat -> effectiveStatus(seat, clock.instant()) == ShowSeatStatus.AVAILABLE)
                .count();
        return new ShowDetailResponse(toShowResponse(show), new ShowAvailabilityResponse(total, available));
    }

    /**
     * Live seat map. Seats under an expired-but-unswept hold are shown as
     * AVAILABLE — the sweeper is an optimization, not a correctness gate.
     */
    @Transactional(readOnly = true)
    public SeatMapResponse seatMap(Long showId) {
        Show show = showRepository.findByIdWithDetails(showId)
                .orElseThrow(() -> ApiException.notFound("Show not found: " + showId));
        Instant now = clock.instant();
        List<SeatMapEntry> entries = showSeatRepository.findByShowIdWithSeat(showId).stream()
                .map(showSeat -> new SeatMapEntry(
                        showSeat.getSeat().getId(),
                        showSeat.getSeat().getRowLabel(),
                        showSeat.getSeat().getSeatNumber(),
                        showSeat.getSeat().label(),
                        showSeat.getSeat().getType(),
                        effectiveStatus(showSeat, now),
                        pricingService.effectivePrice(show, showSeat.getSeat().getType())))
                .sorted(Comparator.comparing(SeatMapEntry::rowLabel).thenComparing(SeatMapEntry::seatNumber))
                .toList();
        return new SeatMapResponse(showId, entries);
    }

    private ShowSeatStatus effectiveStatus(ShowSeat showSeat, Instant now) {
        if (showSeat.getStatus() == ShowSeatStatus.HELD
                && (showSeat.getHold() == null || showSeat.getHold().isExpired(now))) {
            return ShowSeatStatus.AVAILABLE;
        }
        return showSeat.getStatus();
    }

    private ShowResponse toShowResponse(Show show) {
        Map<SeatType, BigDecimal> effective = new EnumMap<>(SeatType.class);
        show.getBasePrices().keySet()
                .forEach(type -> effective.put(type, pricingService.effectivePrice(show, type)));
        return ShowResponse.from(show, effective);
    }
}
