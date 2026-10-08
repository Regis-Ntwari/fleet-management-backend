package com.limoz.fleet.telematics.movement.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Output of {@link DailyMovementCalculator} for one vehicle and one operational day. */
public record MovementResult(
        BigDecimal distanceKm,
        boolean moved,
        Instant firstMovementAt,
        Instant lastMovementAt,
        int drivingMinutes,
        int idleMinutes,
        int nightDrivingMinutes,
        BigDecimal maxSpeedKph,
        int tripsCount,
        BigDecimal startLatitude,
        BigDecimal startLongitude,
        BigDecimal endLatitude,
        BigDecimal endLongitude,
        boolean gpsIssue,
        List<MovementFlag> flags) {

    public MovementResult withTripsCount(int count) {
        return new MovementResult(distanceKm, moved, firstMovementAt, lastMovementAt, drivingMinutes, idleMinutes, nightDrivingMinutes,
                maxSpeedKph, count, startLatitude, startLongitude, endLatitude, endLongitude, gpsIssue, flags);
    }

    public boolean hasFlag(MovementFlag flag) {
        return flags.contains(flag);
    }

    public List<String> flagNames() {
        List<String> names = new ArrayList<>(flags.size());
        for (MovementFlag flag : flags) {
            names.add(flag.name());
        }
        return names;
    }
}
