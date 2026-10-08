package com.limoz.fleet.maintenance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/** Work-done form: what was performed plus optional final labour / other cost and odometer at completion. */
public record MaintenanceCompleteRequest(
        @NotBlank String servicePerformed,
        @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal laborCost,
        @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal otherCost,
        @Min(0) Long odometerKm) {}
