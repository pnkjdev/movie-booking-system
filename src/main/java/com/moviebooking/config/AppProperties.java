package com.moviebooking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.ZoneId;

/**
 * Central, typed view of every business knob the system exposes. All values are
 * overridable via standard Spring configuration (env vars, profiles, etc.).
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String timezone,
        String currency,
        Security security,
        Booking booking,
        Pricing pricing,
        Jobs jobs,
        Seed seed
) {

    public record Security(String jwtSecret, long jwtExpiryMinutes) {
    }

    public record Booking(long holdTtlSeconds, int maxSeatsPerHold) {
    }

    public record Pricing(BigDecimal weekendSurchargePercent) {
    }

    public record Jobs(long holdSweeperIntervalMs,
                       long reminderIntervalMs,
                       long reminderLeadMinutes,
                       long notificationDispatchIntervalMs) {
    }

    public record Seed(boolean enabled) {
    }

    public ZoneId zoneId() {
        return ZoneId.of(timezone);
    }
}
