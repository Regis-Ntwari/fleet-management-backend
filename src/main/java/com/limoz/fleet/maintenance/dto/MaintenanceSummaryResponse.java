package com.limoz.fleet.maintenance.dto;

import com.limoz.fleet.maintenance.domain.MaintenanceType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Period report of maintenance activity, aggregated in the database. */
public record MaintenanceSummaryResponse(
        LocalDate from,
        LocalDate to,
        long jobCount,
        BigDecimal totalCost,
        BigDecimal laborCost,
        BigDecimal partsCost,
        BigDecimal otherCost,
        BigDecimal amountPaid,
        BigDecimal averageDaysInGarage,
        List<TypeCost> costByType,
        List<VehicleCost> topVehiclesByCost) {

    public record TypeCost(MaintenanceType type, long jobCount, BigDecimal totalCost) {}

    public record VehicleCost(Long vehicleId, String plateNumber, String vehicleName, long jobCount, BigDecimal totalCost) {}
}
