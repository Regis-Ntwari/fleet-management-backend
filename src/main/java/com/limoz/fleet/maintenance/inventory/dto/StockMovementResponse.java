package com.limoz.fleet.maintenance.inventory.dto;

import com.limoz.fleet.maintenance.inventory.domain.StockMovementType;

import java.math.BigDecimal;
import java.time.Instant;

public record StockMovementResponse(
        Long id,
        Long sparePartId,
        String partNumber,
        String partName,
        StockMovementType movementType,
        int quantity,
        BigDecimal unitCost,
        int balanceAfter,
        String referenceType,
        Long referenceId,
        String referenceNumber,
        Long maintenanceRecordId,
        Long performedByUserId,
        String performedByName,
        Instant movedAt,
        String notes,
        Instant createdAt) {}
