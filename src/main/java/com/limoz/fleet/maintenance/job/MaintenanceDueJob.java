package com.limoz.fleet.maintenance.job;

import com.limoz.fleet.maintenance.service.MaintenanceScheduleService;

import com.limoz.fleet.maintenance.dto.ScheduleRefreshResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Nightly recalculation of preventive schedules and vehicle maintenance statuses, with due / overdue alerts. */
@Slf4j
@Component
@RequiredArgsConstructor
public class MaintenanceDueJob {

    private final MaintenanceScheduleService scheduleService;

    @Scheduled(cron = "${fleet.jobs.maintenance-due-cron}", zone = "${fleet.timezone}")
    public void refreshSchedules() {
        ScheduleRefreshResponse result = scheduleService.refreshAll();
        log.debug("Maintenance due job finished: {}", result);
    }
}
