package com.limoz.fleet.maintenance.dto;

import com.limoz.fleet.maintenance.domain.MaintenanceType;
import com.limoz.fleet.maintenance.domain.Priority;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Job / intake details. Used by the simple "new maintenance job" screen, the garage intake form and for
 * editing an open job. The vehicle cannot be changed after creation.
 */
public record MaintenanceRequest(
        @NotNull Long vehicleId,
        Instant reportedAt,
        Long customerId,
        @Size(max = 150) String ownerName,
        @Size(max = 80) String department,
        Long driverId,
        @Size(max = 120) String driverName,
        @Size(max = 30) String driverContact,
        @NotBlank String complaint,
        String visibleCondition,
        MaintenanceType maintenanceType,
        Priority priority,
        Long workshopId,
        Long technicianUserId,
        @Size(max = 120) String technicianName,
        Long managerUserId,
        Long incidentId,
        @Min(0) Long odometerKm,
        LocalDate expectedCompletionAt,
        @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal laborCost,
        @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal otherCost,
        String comments) {}
