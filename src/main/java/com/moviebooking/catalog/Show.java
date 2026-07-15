package com.moviebooking.catalog;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;

/**
 * A screening of a movie on a screen. Base prices are configured per seat
 * type at scheduling time; a weekend surcharge is layered on top by the
 * pricing service.
 */
@Entity
@Table(name = "shows", indexes = {
        @Index(name = "idx_show_screen_time", columnList = "screen_id, start_time"),
        @Index(name = "idx_show_movie", columnList = "movie_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Show {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "screen_id", nullable = false)
    private Screen screen;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ShowStatus status = ShowStatus.SCHEDULED;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "show_prices", joinColumns = @JoinColumn(name = "show_id"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "seat_type", length = 20)
    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private Map<SeatType, BigDecimal> basePrices = new EnumMap<>(SeatType.class);

    public boolean isOnWeekend() {
        DayOfWeek day = startTime.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }
}
