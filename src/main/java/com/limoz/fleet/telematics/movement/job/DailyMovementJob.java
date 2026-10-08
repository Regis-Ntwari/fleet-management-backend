package com.limoz.fleet.telematics.movement.job;

import com.limoz.fleet.telematics.movement.service.DailyMovementService;

import com.limoz.fleet.telematics.movement.dto.RecomputeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/** Nightly analysis of the previous operational day for every vehicle. */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyMovementJob {

    private final DailyMovementService movementService;
    private final Clock clock;

    @Scheduled(cron = "${fleet.jobs.daily-movement-cron}", zone = "${fleet.timezone}")
    public void analyseYesterday() {
        LocalDate yesterday = LocalDate.now(clock).minusDays(1);
        RecomputeResponse result = movementService.computeFor(yesterday);
        log.debug("Daily movement job finished for {}: {}", yesterday, result);
    }
}
