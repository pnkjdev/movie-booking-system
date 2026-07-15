package com.moviebooking.catalog;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ShowSeatRepository extends JpaRepository<ShowSeat, Long> {

    /**
     * Locks the requested seats with SELECT ... FOR UPDATE in deterministic
     * id order. Every writer (hold, confirm, release, sweep) goes through an
     * ordered lock like this one, which rules out lock-order deadlocks and
     * serializes competing bookings for the same seats.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select ss from ShowSeat ss
            join fetch ss.seat
            where ss.show.id = :showId and ss.seat.id in :seatIds
            order by ss.id
            """)
    List<ShowSeat> lockByShowIdAndSeatIds(@Param("showId") Long showId,
                                          @Param("seatIds") Collection<Long> seatIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select ss from ShowSeat ss
            join fetch ss.seat
            where ss.hold.id = :holdId
            order by ss.id
            """)
    List<ShowSeat> lockByHoldId(@Param("holdId") Long holdId);

    @Query("""
            select ss from ShowSeat ss
            join fetch ss.seat
            left join fetch ss.hold
            where ss.show.id = :showId
            """)
    List<ShowSeat> findByShowIdWithSeat(@Param("showId") Long showId);

    @Query("select ss from ShowSeat ss join fetch ss.seat where ss.hold.id = :holdId order by ss.id")
    List<ShowSeat> findByHoldId(@Param("holdId") Long holdId);

    long countByShowIdAndStatus(Long showId, ShowSeatStatus status);

    long countByShowId(Long showId);
}
