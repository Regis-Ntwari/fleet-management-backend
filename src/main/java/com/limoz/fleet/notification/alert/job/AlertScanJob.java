package com.limoz.fleet.notification.alert.job;

import com.limoz.fleet.notification.alert.domain.Alert;
import com.limoz.fleet.notification.alert.service.AlertService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodic alert scan (every 15 minutes by default, see {@code fleet.jobs.alert-scan-cron}). */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertScanJob {

    private final AlertService alertService;

    @Scheduled(cron = "${fleet.jobs.alert-scan-cron}", zone = "${fleet.timezone}")
    public void scan() {
        log.debug("Alert scan job finished: {}", alertService.runScan());
    }
}
