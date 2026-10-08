package com.limoz.fleet.fuel.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Aggregated fuel figures for one vehicle (or the whole fleet) over a date range. */
public record FuelSummaryResponse(
        Long vehicleId,
        String plateNumber,
        String vehicleName,
        LocalDate from,
        LocalDate to,
        long transactionCount,
        BigDecimal totalLitres,
        BigDecimal totalCost,
        BigDecimal totalDistanceKm,
        BigDecimal averageConsumptionLPer100km,
        BigDecimal averageKmPerLitre,
        BigDecimal averagePricePerLitre,
        long anomalyCount) {}
