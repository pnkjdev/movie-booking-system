package com.moviebooking.discount;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DiscountCodeRepository extends JpaRepository<DiscountCode, Long> {

    Optional<DiscountCode> findByCodeIgnoreCase(String code);

    /**
     * Atomically consumes one redemption. Returns 0 when the total usage
     * limit is already exhausted, which makes limit enforcement safe under
     * concurrency without locking the code row for the whole booking.
     */
    @Modifying
    @Query("""
            update DiscountCode d set d.timesUsed = d.timesUsed + 1
            where d.id = :id and (d.totalUsageLimit is null or d.timesUsed < d.totalUsageLimit)
            """)
    int tryConsume(@Param("id") Long id);

    /** Returns a redemption when a pending booking expires unpaid. */
    @Modifying
    @Query("update DiscountCode d set d.timesUsed = d.timesUsed - 1 where d.id = :id and d.timesUsed > 0")
    int releaseOne(@Param("id") Long id);
}
