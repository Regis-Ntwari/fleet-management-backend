package com.limoz.fleet.maintenance.inventory.dto;

import com.limoz.fleet.maintenance.inventory.domain.StockMovementType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Manual stock movement. Quantity is a magnitude for IN / OUT / RETURN and a signed correction for ADJUSTMENT.
 * An IN without a reference receives a generated purchase number (PO-xxxx).
 */
public record StockMovementRequest(
        @NotNull Long sparePartId,
        @NotNull StockMovementType movementType,
        @NotNull Integer quantity,
        @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal unitCost,
        @Size(max = 30) String referenceType,
        @Size(max = 40) String referenceNumber,
        Instant movedAt,
        @Size(max = 255) String notes) {}
