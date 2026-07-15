package com.moviebooking.hold;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SeatHoldRepository extends JpaRepository<SeatHold, Long> {

    /**
     * Serializes the payment-confirmation path against the expiry sweeper:
     * whichever transaction locks the hold row first decides its fate.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from SeatHold h where h.id = :id")
    Optional<SeatHold> lockById(@Param("id") Long id);

    @Query("select h.id from SeatHold h where h.status = :status and h.expiresAt < :cutoff")
    List<Long> findIdsByStatusAndExpiresAtBefore(@Param("status") SeatHoldStatus status,
                                                 @Param("cutoff") Instant cutoff);

    List<SeatHold> findByShowIdAndStatus(Long showId, SeatHoldStatus status);
}
