package com.moviebooking.refund;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RefundPolicyRepository extends JpaRepository<RefundPolicy, Long> {

    Optional<RefundPolicy> findByDefaultPolicyTrue();

    List<RefundPolicy> findByDefaultPolicyTrueAndIdNot(Long id);

    boolean existsByNameIgnoreCase(String name);
}
