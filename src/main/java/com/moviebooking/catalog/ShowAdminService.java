package com.moviebooking.catalog;

import com.moviebooking.catalog.dto.ShowDtos.ShowCreateRequest;
import com.moviebooking.catalog.dto.ShowDtos.ShowResponse;
import com.moviebooking.common.error.ApiException;
import com.moviebooking.common.error.ErrorCode;
import com.moviebooking.pricing.PricingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShowAdminService {

    private final ShowRepository showRepository;
    private final ScreenRepository screenRepository;
    private final MovieRepository movieRepository;
    private final SeatRepository seatRepository;
    private final ShowSeatRepository showSeatRepository;
    private final PricingService pricingService;

    /**
     * Schedules a show and materializes one inventory row per physical seat.
     * Rejects overlapping shows on the same screen and price maps that do
     * not cover every seat type present in the screen's layout.
     */
    @Transactional
    public ShowResponse createShow(ShowCreateRequest request) {
        Screen screen = screenRepository.findById(request.screenId())
                .orElseThrow(() -> ApiException.notFound("Screen not found: " + request.screenId()));
        Movie movie = movieRepository.findById(request.movieId())
                .filter(Movie::isActive)
                .orElseThrow(() -> ApiException.notFound("Active movie not found: " + request.movieId()));

        List<Seat> seats = seatRepository.findByScreenIdOrderByRowLabelAscSeatNumberAsc(screen.getId());
        if (seats.isEmpty()) {
            throw ApiException.conflict(ErrorCode.CONFLICT,
                    "Screen has no seat layout yet; define seats before scheduling shows");
        }

        Set<SeatType> layoutTypes = seats.stream().map(Seat::getType).collect(Collectors.toSet());
        Set<SeatType> missing = layoutTypes.stream()
                .filter(type -> !request.prices().containsKey(type))
                .collect(Collectors.toSet());
        if (!missing.isEmpty()) {
            throw ApiException.badRequest("Missing prices for seat types present on this screen: " + missing);
        }

        LocalDateTime endTime = request.startTime().plusMinutes(movie.getDurationMinutes());
        if (showRepository.existsOverlapping(screen.getId(), request.startTime(), endTime,
                ShowStatus.SCHEDULED, null)) {
            throw ApiException.conflict(ErrorCode.CONFLICT,
                    "Another show is already scheduled on this screen during that time window");
        }

        Map<SeatType, BigDecimal> prices = new EnumMap<>(SeatType.class);
        request.prices().forEach(prices::put);

        Show show = showRepository.save(Show.builder()
                .screen(screen)
                .movie(movie)
                .startTime(request.startTime())
                .endTime(endTime)
                .status(ShowStatus.SCHEDULED)
                .basePrices(prices)
                .build());

        List<ShowSeat> inventory = seats.stream()
                .map(seat -> ShowSeat.builder()
                        .show(show)
                        .seat(seat)
                        .status(ShowSeatStatus.AVAILABLE)
                        .build())
                .toList();
        showSeatRepository.saveAll(inventory);

        log.info("Scheduled show {} ({} on screen {}) with {} seats",
                show.getId(), movie.getTitle(), screen.getId(), inventory.size());
        return toResponse(show.getId());
    }

    @Transactional(readOnly = true)
    public ShowResponse toResponse(Long showId) {
        Show show = showRepository.findByIdWithDetails(showId)
                .orElseThrow(() -> ApiException.notFound("Show not found: " + showId));
        Map<SeatType, BigDecimal> effective = new EnumMap<>(SeatType.class);
        show.getBasePrices().keySet()
                .forEach(type -> effective.put(type, pricingService.effectivePrice(show, type)));
        return ShowResponse.from(show, effective);
    }
}
