package com.limoz.fleet.maintenance.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Preventive schedule of a service type on a vehicle. Missing intervals fall back to the service type's
 * defaults, then to the maintenance.default_interval_* settings; a missing last service defaults to the
 * vehicle's current odometer and today.
 */
public record ScheduleRequest(
        @NotNull Long vehicleId,
        @NotNull Long serviceTypeId,
        @Min(1) Integer intervalKm,
        @Min(1) Integer intervalDays,
        @Min(0) Long lastServiceOdometer,
        LocalDate lastServiceDate,
        Boolean active,
        @Size(max = 255) String notes) {}
