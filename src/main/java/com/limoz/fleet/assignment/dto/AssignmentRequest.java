package com.limoz.fleet.assignment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record AssignmentRequest(
        @NotNull Long vehicleId,
        @NotNull Long driverId,
        Instant startAt,
        @Size(max = 255) String purpose,
        @Min(0) Long odometerAtAssignment,
        @Size(max = 500) String comments) {}
