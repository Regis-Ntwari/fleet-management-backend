package com.limoz.fleet.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record UtilizationResponse(LocalDate from, LocalDate to, long fleetSize, BigDecimal averageUtilisationPercent,
                                  List<DailyPoint> daily, List<VehicleUtilization> vehicles) {

    public record VehicleUtilization(Long vehicleId, String plateNumber, String category, long daysUsed, long daysInRange,
                                     BigDecimal utilisationPercent, BigDecimal distanceKm, String classification) {}
}
