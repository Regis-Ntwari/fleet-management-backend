package com.limoz.fleet.telematics.domain;

import com.limoz.fleet.telematics.service.TelematicsProvider;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One GPS fix as returned by a {@link TelematicsProvider}. Provider-neutral: the provider maps its own
 * unit identifiers to {@code externalDeviceId}, which is matched against {@link TelematicsDevice#getExternalDeviceId()}.
 *
 * @param externalDeviceId provider-side unit id
 * @param recordedAt       time of the fix (UTC)
 * @param latitude         decimal degrees, -90..90
 * @param longitude        decimal degrees, -180..180
 * @param speedKph         ground speed, null = unknown (treated as 0)
 * @param heading          compass heading 0..360, optional
 * @param odometerKm       cumulative odometer reported by the unit, optional
 * @param ignitionOn       ignition state, optional
 * @param batteryVoltage   unit/vehicle battery voltage, optional
 * @param fuelLevelLitres  fuel level from the fuel sensor, optional
 */
public record PositionSample(
        String externalDeviceId,
        Instant recordedAt,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal speedKph,
        BigDecimal heading,
        BigDecimal odometerKm,
        Boolean ignitionOn,
        BigDecimal batteryVoltage,
        BigDecimal fuelLevelLitres) {}
