package com.limoz.fleet.telematics.movement;

import com.limoz.fleet.telematics.VehiclePosition;

import java.math.BigDecimal;
import java.time.Instant;

/** Minimal view of a position used by {@link DailyMovementCalculator}; keeps the calculator free of JPA. */
public record MovementSample(Instant recordedAt, BigDecimal latitude, BigDecimal longitude, BigDecimal speedKph,
                             BigDecimal odometerKm, Boolean ignitionOn) {

    public static MovementSample from(VehiclePosition p) {
        return new MovementSample(p.getRecordedAt(), p.getLatitude(), p.getLongitude(), p.getSpeedKph(), p.getOdometerKm(), p.getIgnitionOn());
    }

    public double speed() {
        return speedKph == null ? 0d : speedKph.doubleValue();
    }

    public boolean ignition() {
        return Boolean.TRUE.equals(ignitionOn);
    }
}
