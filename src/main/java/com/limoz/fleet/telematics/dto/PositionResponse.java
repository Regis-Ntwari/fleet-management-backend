package com.limoz.fleet.telematics.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PositionResponse(
        Long id,
        Long vehicleId,
        Long deviceId,
        Instant recordedAt,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal speedKph,
        BigDecimal heading,
        BigDecimal odometerKm,
        Boolean ignitionOn,
        BigDecimal batteryVoltage,
        BigDecimal fuelLevelLitres,
        String source) {}
