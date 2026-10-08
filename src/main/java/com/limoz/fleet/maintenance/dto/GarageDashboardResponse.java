package com.limoz.fleet.maintenance.dto;

import com.limoz.fleet.maintenance.domain.GarageDayBand;
import com.limoz.fleet.maintenance.domain.MaintenanceRecordStatus;
import com.limoz.fleet.maintenance.domain.Priority;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record GarageDashboardResponse(
        Map<MaintenanceRecordStatus, Long> statusCounts,
        long vehiclesInGarage,
        List<GarageVehicleRow> inGarage,
        long partsAwaitingApproval,
        long jobsWaitingForParts,
        long completedThisMonth,
        BigDecimal totalCostThisMonth,
        BigDecimal averageDaysInGarage) {

    public record GarageVehicleRow(Long id, String maintenanceNumber, String intakeNumber, Long vehicleId, String plateNumber,
                                   String vehicleName, String categoryName, String ownerName, String department, Instant reportedAt,
                                   long daysInGarage, GarageDayBand band, String technicianName, String workshopName,
                                   MaintenanceRecordStatus status, Priority priority) {}
}
