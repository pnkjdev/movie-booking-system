package com.moviebooking.hold;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;

/**
 * Background safety net that returns timed-out holds to inventory. Correctness
 * does not depend on it: the hold/booking paths also treat expired holds as
 * dead lazily. Each hold expires in its own transaction so one poisoned row
 * cannot stall the sweep.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HoldSweeper {

    private final SeatHoldRepository seatHoldRepository;
    private final SeatHoldService seatHoldService;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.jobs.hold-sweeper-interval-ms}")
    public void sweepExpiredHolds() {
        List<Long> expiredIds = seatHoldRepository.findIdsByStatusAndExpiresAtBefore(
                SeatHoldStatus.ACTIVE, clock.instant());
        if (expiredIds.isEmpty()) {
            return;
        }
        log.debug("Sweeping {} expired hold(s)", expiredIds.size());
        for (Long holdId : expiredIds) {
            try {
                seatHoldService.expireHold(holdId);
            } catch (Exception ex) {
                log.warn("Failed to expire hold {}: {}", holdId, ex.getMessage());
            }
        }
    }
}
