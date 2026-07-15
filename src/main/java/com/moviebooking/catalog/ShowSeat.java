package com.moviebooking.catalog;

import com.moviebooking.hold.SeatHold;
import jakarta.persistence.Column;
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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Per-show inventory record for one physical seat. This is the row that
 * concurrent bookings contend on: all state transitions happen under a
 * pessimistic row lock acquired in deterministic (id) order to avoid
 * deadlocks, with an optimistic {@link Version} column as a second line of
 * defence.
 */
@Entity
@Table(name = "show_seats",
        uniqueConstraints = @UniqueConstraint(name = "uk_show_seat", columnNames = {"show_id", "seat_id"}),
        indexes = @Index(name = "idx_show_seat_status", columnList = "show_id, status"))
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ShowSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "show_id", nullable = false)
    private Show show;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ShowSeatStatus status = ShowSeatStatus.AVAILABLE;

    /** The active hold occupying this seat, when status is HELD. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hold_id")
    private SeatHold hold;

    @Version
    private long version;

    public void markHeld(SeatHold newHold) {
        this.status = ShowSeatStatus.HELD;
        this.hold = newHold;
    }

    public void markBooked() {
        this.status = ShowSeatStatus.BOOKED;
        this.hold = null;
    }

    public void release() {
        this.status = ShowSeatStatus.AVAILABLE;
        this.hold = null;
    }
}
