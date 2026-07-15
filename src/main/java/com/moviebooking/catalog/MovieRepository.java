package com.moviebooking.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MovieRepository extends JpaRepository<Movie, Long> {

    List<Movie> findByActiveTrueOrderByTitle();

    boolean existsByTitleIgnoreCaseAndLanguageIgnoreCase(String title, String language);
}
