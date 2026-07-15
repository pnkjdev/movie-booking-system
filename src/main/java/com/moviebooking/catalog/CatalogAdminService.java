package com.moviebooking.catalog;

import com.moviebooking.catalog.dto.CatalogDtos.CityRequest;
import com.moviebooking.catalog.dto.CatalogDtos.CityResponse;
import com.moviebooking.catalog.dto.CatalogDtos.MovieRequest;
import com.moviebooking.catalog.dto.CatalogDtos.MovieResponse;
import com.moviebooking.catalog.dto.CatalogDtos.ScreenRequest;
import com.moviebooking.catalog.dto.CatalogDtos.ScreenResponse;
import com.moviebooking.catalog.dto.CatalogDtos.SeatLayoutRequest;
import com.moviebooking.catalog.dto.CatalogDtos.SeatResponse;
import com.moviebooking.catalog.dto.CatalogDtos.SeatRowSpec;
import com.moviebooking.catalog.dto.CatalogDtos.TheaterRequest;
import com.moviebooking.catalog.dto.CatalogDtos.TheaterResponse;
import com.moviebooking.common.error.ApiException;
import com.moviebooking.common.error.ErrorCode;
import com.moviebooking.refund.RefundPolicy;
import com.moviebooking.refund.RefundPolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CatalogAdminService {

    private final CityRepository cityRepository;
    private final TheaterRepository theaterRepository;
    private final ScreenRepository screenRepository;
    private final SeatRepository seatRepository;
    private final MovieRepository movieRepository;
    private final ShowRepository showRepository;
    private final RefundPolicyRepository refundPolicyRepository;

    // ---- Cities ----

    @Transactional
    public CityResponse createCity(CityRequest request) {
        if (cityRepository.existsByNameIgnoreCase(request.name().trim())) {
            throw ApiException.conflict(ErrorCode.CONFLICT, "City already exists: " + request.name());
        }
        City city = cityRepository.save(City.builder()
                .name(request.name().trim())
                .state(trimOrNull(request.state()))
                .build());
        return CityResponse.from(city);
    }

    @Transactional
    public CityResponse updateCity(Long id, CityRequest request) {
        City city = cityRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("City not found: " + id));
        city.setName(request.name().trim());
        city.setState(trimOrNull(request.state()));
        return CityResponse.from(city);
    }

    @Transactional
    public void deleteCity(Long id) {
        if (!cityRepository.existsById(id)) {
            throw ApiException.notFound("City not found: " + id);
        }
        if (!theaterRepository.findByCityIdOrderByName(id).isEmpty()) {
            throw ApiException.conflict(ErrorCode.CONFLICT, "City has theaters and cannot be deleted");
        }
        cityRepository.deleteById(id);
    }

    // ---- Theaters ----

    @Transactional
    public TheaterResponse createTheater(TheaterRequest request) {
        City city = cityRepository.findById(request.cityId())
                .orElseThrow(() -> ApiException.notFound("City not found: " + request.cityId()));
        if (theaterRepository.existsByCityIdAndNameIgnoreCase(city.getId(), request.name().trim())) {
            throw ApiException.conflict(ErrorCode.CONFLICT,
                    "Theater '" + request.name() + "' already exists in " + city.getName());
        }
        Theater theater = theaterRepository.save(Theater.builder()
                .name(request.name().trim())
                .address(trimOrNull(request.address()))
                .city(city)
                .refundPolicy(resolvePolicy(request.refundPolicyId()))
                .build());
        return TheaterResponse.from(theater);
    }

    @Transactional
    public TheaterResponse updateTheater(Long id, TheaterRequest request) {
        Theater theater = theaterRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Theater not found: " + id));
        City city = cityRepository.findById(request.cityId())
                .orElseThrow(() -> ApiException.notFound("City not found: " + request.cityId()));
        theater.setName(request.name().trim());
        theater.setAddress(trimOrNull(request.address()));
        theater.setCity(city);
        theater.setRefundPolicy(resolvePolicy(request.refundPolicyId()));
        return TheaterResponse.from(theater);
    }

    // ---- Screens & seat layout ----

    @Transactional
    public ScreenResponse createScreen(Long theaterId, ScreenRequest request) {
        Theater theater = theaterRepository.findById(theaterId)
                .orElseThrow(() -> ApiException.notFound("Theater not found: " + theaterId));
        if (screenRepository.existsByTheaterIdAndNameIgnoreCase(theaterId, request.name().trim())) {
            throw ApiException.conflict(ErrorCode.CONFLICT,
                    "Screen '" + request.name() + "' already exists in this theater");
        }
        Screen screen = screenRepository.save(Screen.builder()
                .name(request.name().trim())
                .theater(theater)
                .build());
        return ScreenResponse.from(screen, 0);
    }

    /**
     * Replaces the full seat layout of a screen. Disallowed once shows exist
     * on the screen, because show inventory references physical seats.
     */
    @Transactional
    public List<SeatResponse> replaceSeatLayout(Long screenId, SeatLayoutRequest request) {
        Screen screen = screenRepository.findById(screenId)
                .orElseThrow(() -> ApiException.notFound("Screen not found: " + screenId));
        if (!showRepository.findByScreenId(screenId).isEmpty()) {
            throw ApiException.conflict(ErrorCode.CONFLICT,
                    "Seat layout cannot be changed after shows are scheduled on this screen");
        }
        Set<String> rowLabels = new HashSet<>();
        for (SeatRowSpec row : request.rows()) {
            if (!rowLabels.add(row.rowLabel().trim().toUpperCase(Locale.ROOT))) {
                throw ApiException.badRequest("Duplicate row label: " + row.rowLabel());
            }
        }
        seatRepository.deleteByScreenId(screenId);
        List<Seat> seats = new ArrayList<>();
        for (SeatRowSpec row : request.rows()) {
            for (int number = 1; number <= row.seatCount(); number++) {
                seats.add(Seat.builder()
                        .screen(screen)
                        .rowLabel(row.rowLabel().trim().toUpperCase(Locale.ROOT))
                        .seatNumber(number)
                        .type(row.type())
                        .build());
            }
        }
        return seatRepository.saveAll(seats).stream().map(SeatResponse::from).toList();
    }

    // ---- Movies ----

    @Transactional
    public MovieResponse createMovie(MovieRequest request) {
        if (movieRepository.existsByTitleIgnoreCaseAndLanguageIgnoreCase(
                request.title().trim(), request.language() == null ? "" : request.language().trim())) {
            throw ApiException.conflict(ErrorCode.CONFLICT, "Movie already exists: " + request.title());
        }
        Movie movie = movieRepository.save(Movie.builder()
                .title(request.title().trim())
                .language(trimOrNull(request.language()))
                .genre(trimOrNull(request.genre()))
                .durationMinutes(request.durationMinutes())
                .certification(trimOrNull(request.certification()))
                .description(trimOrNull(request.description()))
                .active(true)
                .build());
        return MovieResponse.from(movie);
    }

    @Transactional
    public MovieResponse updateMovie(Long id, MovieRequest request) {
        Movie movie = movieRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Movie not found: " + id));
        movie.setTitle(request.title().trim());
        movie.setLanguage(trimOrNull(request.language()));
        movie.setGenre(trimOrNull(request.genre()));
        movie.setDurationMinutes(request.durationMinutes());
        movie.setCertification(trimOrNull(request.certification()));
        movie.setDescription(trimOrNull(request.description()));
        return MovieResponse.from(movie);
    }

    /** Soft delete: hides the movie from browsing; existing shows keep running. */
    @Transactional
    public MovieResponse deactivateMovie(Long id) {
        Movie movie = movieRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Movie not found: " + id));
        movie.setActive(false);
        return MovieResponse.from(movie);
    }

    private RefundPolicy resolvePolicy(Long refundPolicyId) {
        if (refundPolicyId == null) {
            return null;
        }
        return refundPolicyRepository.findById(refundPolicyId)
                .orElseThrow(() -> ApiException.notFound("Refund policy not found: " + refundPolicyId));
    }

    private String trimOrNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
