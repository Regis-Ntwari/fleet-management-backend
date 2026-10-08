package com.limoz.fleet.maintenance.dto;

import com.limoz.fleet.maintenance.ScheduleStatus;
import com.limoz.fleet.vehicle.dto.VehicleSummary;

import java.time.Instant;
import java.time.LocalDate;

public record ScheduleResponse(
        Long id,
        VehicleSummary vehicle,
        long vehicleOdometerKm,
        Long serviceTypeId,
        String serviceTypeCode,
        String serviceTypeName,
        Integer intervalKm,
        Integer intervalDays,
        Long lastServiceOdometer,
        LocalDate lastServiceDate,
        Long lastMaintenanceRecordId,
        Long nextServiceOdometer,
        LocalDate nextServiceDate,
        Long kmRemaining,
        Long daysRemaining,
        ScheduleStatus status,
        boolean active,
        String notes,
        Instant updatedAt) {}
