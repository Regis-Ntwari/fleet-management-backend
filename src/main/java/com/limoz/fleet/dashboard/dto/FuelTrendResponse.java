package com.limoz.fleet.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FuelTrendResponse(LocalDate from, LocalDate to, BigDecimal totalLitres, BigDecimal totalCost,
                                BigDecimal averageConsumptionLPer100Km, long anomalies, List<DailyPoint> daily, List<VehicleFuel> topConsumers) {

    public record VehicleFuel(Long vehicleId, String plateNumber, BigDecimal litres, BigDecimal cost, BigDecimal distanceKm, BigDecimal lPer100Km) {}
}
