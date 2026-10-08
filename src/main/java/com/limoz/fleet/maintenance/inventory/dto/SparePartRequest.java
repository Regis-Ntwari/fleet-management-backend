package com.limoz.fleet.maintenance.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Catalogue data of a spare part. {@code openingStock} is only honoured on creation (it becomes an IN movement);
 * later stock changes must go through stock movements.
 */
public record SparePartRequest(
        @NotBlank @Size(max = 40) String partNumber,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 60) String category,
        @Size(max = 20) String unit,
        @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal unitCost,
        @Size(max = 120) String supplier,
        @Min(0) Integer minimumStock,
        @Min(0) Integer openingStock,
        @Size(max = 80) String location,
        Boolean active,
        @Size(max = 255) String notes) {}
