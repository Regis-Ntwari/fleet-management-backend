package com.limoz.fleet.trip.domain;

import com.limoz.fleet.common.exception.BusinessRuleException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/** Pure trip arithmetic (distance, duration) shared by ad hoc trips and booking deployments. */
public final class TripCalculations {

    private TripCalculations() {}

    /** Distance in km (1 dp) between two odometer readings; the end reading may not be lower than the start. */
    public static BigDecimal distanceKm(long startOdometerKm, long endOdometerKm) {
        if (endOdometerKm < startOdometerKm) {
            throw new BusinessRuleException("END_ODOMETER_BELOW_START",
                    "End odometer " + endOdometerKm + " km cannot be lower than the start reading of " + startOdometerKm + " km");
        }
        return BigDecimal.valueOf(endOdometerKm - startOdometerKm).setScale(1);
    }

    /** Whole minutes between start and end (rounded down); the end may not precede the start. */
    public static int durationMinutes(Instant startedAt, Instant endedAt) {
        if (endedAt.isBefore(startedAt)) {
            throw new BusinessRuleException("END_BEFORE_START", "Trip end " + endedAt + " cannot precede its start " + startedAt);
        }
        return (int) Duration.between(startedAt, endedAt).toMinutes();
    }
}
