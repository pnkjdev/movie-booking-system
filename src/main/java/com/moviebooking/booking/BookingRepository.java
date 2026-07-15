package com.moviebooking.booking;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> lockById(@Param("id") Long id);

    @Query("""
            select b from Booking b
            join fetch b.show s
            join fetch s.movie
            join fetch s.screen sc
            join fetch sc.theater
            where b.user.id = :userId
            order by b.createdAt desc
            """)
    List<Booking> findAllByUserIdWithDetails(@Param("userId") Long userId);

    Optional<Booking> findByReference(String reference);

    Optional<Booking> findByHoldId(Long holdId);

    List<Booking> findByShowIdAndStatusIn(Long showId, Collection<BookingStatus> statuses);

    long countByUserIdAndDiscountCodeIgnoreCaseAndStatusIn(Long userId, String discountCode,
                                                           Collection<BookingStatus> statuses);

    /**
     * Confirmed bookings whose show starts inside the reminder window and
     * that have not been reminded yet.
     */
    @Query("""
            select b from Booking b
            join fetch b.show s
            join fetch s.movie
            join fetch s.screen sc
            join fetch sc.theater
            join fetch b.user
            where b.status = :status
              and b.reminderSentAt is null
              and s.startTime >= :from and s.startTime <= :to
            """)
    List<Booking> findDueForReminder(@Param("status") BookingStatus status,
                                     @Param("from") LocalDateTime from,
                                     @Param("to") LocalDateTime to);
}
