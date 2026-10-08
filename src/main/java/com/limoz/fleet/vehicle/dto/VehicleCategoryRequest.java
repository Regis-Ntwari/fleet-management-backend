package com.limoz.fleet.vehicle.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record VehicleCategoryRequest(
        @NotBlank @Size(max = 30) @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "code must be UPPER_SNAKE_CASE") String code,
        @NotBlank @Size(max = 80) String name,
        @Size(max = 255) String description,
        @Min(0) Integer minSeats,
        @Min(0) Integer maxSeats,
        @DecimalMin("0") BigDecimal defaultDayRate,
        Boolean active,
        Integer sortOrder) {}
