package com.limoz.fleet.maintenance;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Pure preventive-maintenance arithmetic: next service point from the last service and the interval,
 * and the OK / DUE_SOON / OVERDUE decision against the vehicle's odometer and today's date.
 */
public final class ScheduleCalculator {

    private ScheduleCalculator() {}

    public record Next(Long nextServiceOdometer, LocalDate nextServiceDate) {}

    /** Next odometer / date; each is null when the matching interval or last-service value is unknown. */
    public static Next next(Long lastServiceOdometer, LocalDate lastServiceDate, Integer intervalKm, Integer intervalDays) {
        Long nextOdometer = lastServiceOdometer != null && intervalKm != null ? lastServiceOdometer + intervalKm : null;
        LocalDate nextDate = lastServiceDate != null && intervalDays != null ? lastServiceDate.plusDays(intervalDays) : null;
        return new Next(nextOdometer, nextDate);
    }

    /**
     * OVERDUE when the vehicle has reached the next odometer or the next date has arrived; DUE_SOON when either is
     * within the configured warning window; otherwise OK.
     */
    public static ScheduleStatus status(long vehicleOdometerKm, LocalDate today, Long nextServiceOdometer, LocalDate nextServiceDate,
                                        int dueSoonKm, int dueSoonDays) {
        Long kmRemaining = kmRemaining(vehicleOdometerKm, nextServiceOdometer);
        Long daysRemaining = daysRemaining(today, nextServiceDate);
        if ((kmRemaining != null && kmRemaining <= 0) || (daysRemaining != null && daysRemaining <= 0)) {
            return ScheduleStatus.OVERDUE;
        }
        if ((kmRemaining != null && kmRemaining <= dueSoonKm) || (daysRemaining != null && daysRemaining <= dueSoonDays)) {
            return ScheduleStatus.DUE_SOON;
        }
        return ScheduleStatus.OK;
    }

    public static Long kmRemaining(long vehicleOdometerKm, Long nextServiceOdometer) {
        return nextServiceOdometer == null ? null : nextServiceOdometer - vehicleOdometerKm;
    }

    public static Long daysRemaining(LocalDate today, LocalDate nextServiceDate) {
        return nextServiceDate == null ? null : ChronoUnit.DAYS.between(today, nextServiceDate);
    }
}
