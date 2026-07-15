package com.moviebooking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * A single injectable {@link Clock} so that every time-sensitive rule
 * (hold expiry, weekend pricing, refund cutoffs) is deterministic in tests.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(AppProperties properties) {
        return Clock.system(properties.zoneId());
    }
}
