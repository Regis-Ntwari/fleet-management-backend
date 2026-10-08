package com.limoz.fleet.assignment.dto;

import com.limoz.fleet.assignment.AssignmentStatus;
import com.limoz.fleet.driver.dto.DriverSummary;
import com.limoz.fleet.vehicle.dto.VehicleSummary;

import java.time.Instant;

public record AssignmentResponse(
        Long id,
        VehicleSummary vehicle,
        DriverSummary driver,
        Instant startAt,
        Instant endAt,
        Long assignedByUserId,
        Long endedByUserId,
        String purpose,
        Long odometerAtAssignment,
        Long odometerAtReturn,
        Long distanceKm,
        AssignmentStatus status,
        String comments,
        Instant createdAt,
        String createdBy) {}
