package com.limoz.fleet.maintenance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record MaintenanceTaskRequest(
        Long serviceTypeId,
        @NotBlank @Size(max = 255) String description,
        @DecimalMin("0") @Digits(integer = 4, fraction = 2) BigDecimal laborHours,
        @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal laborCost,
        @Size(max = 255) String notes,
        Integer sortOrder) {}
