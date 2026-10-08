package com.limoz.fleet.maintenance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** A part line: either from the store catalogue ({@code sparePartId}) or free text for externally sourced parts. */
public record MaintenancePartRequest(
        Long sparePartId,
        @Size(max = 120) String partName,
        @Size(max = 40) String partNumber,
        @NotNull @Min(1) Integer quantity,
        @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal unitCost) {}
