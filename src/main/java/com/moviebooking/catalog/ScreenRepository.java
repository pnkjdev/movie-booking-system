package com.moviebooking.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScreenRepository extends JpaRepository<Screen, Long> {

    List<Screen> findByTheaterIdOrderByName(Long theaterId);

    boolean existsByTheaterIdAndNameIgnoreCase(Long theaterId, String name);
}
