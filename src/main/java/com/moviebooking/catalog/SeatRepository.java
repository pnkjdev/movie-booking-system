package com.moviebooking.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByScreenIdOrderByRowLabelAscSeatNumberAsc(Long screenId);

    long countByScreenId(Long screenId);

    void deleteByScreenId(Long screenId);
}
