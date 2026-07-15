package com.moviebooking.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ShowRepository extends JpaRepository<Show, Long> {

    @Query("""
            select (count(s) > 0) from Show s
            where s.screen.id = :screenId
              and s.status = :status
              and s.startTime < :end
              and s.endTime > :start
              and (:excludeShowId is null or s.id <> :excludeShowId)
            """)
    boolean existsOverlapping(@Param("screenId") Long screenId,
                              @Param("start") LocalDateTime start,
                              @Param("end") LocalDateTime end,
                              @Param("status") ShowStatus status,
                              @Param("excludeShowId") Long excludeShowId);

    @Query("""
            select s from Show s
            join fetch s.movie m
            join fetch s.screen sc
            join fetch sc.theater t
            join fetch t.city c
            where s.status = :status
              and (:cityId is null or c.id = :cityId)
              and (:movieId is null or m.id = :movieId)
              and (:theaterId is null or t.id = :theaterId)
              and s.startTime >= :from and s.startTime < :to
            order by s.startTime
            """)
    List<Show> search(@Param("status") ShowStatus status,
                      @Param("cityId") Long cityId,
                      @Param("movieId") Long movieId,
                      @Param("theaterId") Long theaterId,
                      @Param("from") LocalDateTime from,
                      @Param("to") LocalDateTime to);

    @Query("""
            select s from Show s
            join fetch s.movie
            join fetch s.screen sc
            join fetch sc.theater t
            join fetch t.city
            where s.id = :id
            """)
    Optional<Show> findByIdWithDetails(@Param("id") Long id);

    List<Show> findByScreenId(Long screenId);
}
