package com.moviebooking.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TheaterRepository extends JpaRepository<Theater, Long> {

    List<Theater> findByCityIdOrderByName(Long cityId);

    boolean existsByCityIdAndNameIgnoreCase(Long cityId, String name);

    List<Theater> findByRefundPolicyId(Long refundPolicyId);
}
