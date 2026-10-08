package com.limoz.fleet.telematics.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One position row submitted for ingestion. The vehicle is identified by {@code vehicleId}, {@code plateNumber}
 * or {@code externalDeviceId} (first non-blank wins). Field validation is done row by row by the ingest service
 * so that a bad row is reported in {@link IngestResult} instead of rejecting the whole batch.
 */
public record PositionInput(
        Long vehicleId,
        String plateNumber,
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
