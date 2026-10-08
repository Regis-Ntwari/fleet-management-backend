package com.limoz.fleet.trip.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public record TripCompleteRequest(
        @NotNull(message = "End odometer reading is required") @Min(0) Long endOdometerKm,
        Instant endedAt,
        @DecimalMin("0") BigDecimal fuelUsedLitres,
        @DecimalMin("0") BigDecimal maxSpeedKph,
        String notes) {}
