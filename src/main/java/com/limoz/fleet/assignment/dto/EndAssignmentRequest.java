package com.limoz.fleet.assignment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record EndAssignmentRequest(Instant endAt, @Min(0) Long odometerAtReturn, @Size(max = 500) String comments, boolean cancelled) {}
