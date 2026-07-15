package com.moviebooking.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables the background jobs: hold-expiry sweeper, notification dispatcher
 * and show reminders. Kept separate so tests can exclude scheduling cleanly.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
