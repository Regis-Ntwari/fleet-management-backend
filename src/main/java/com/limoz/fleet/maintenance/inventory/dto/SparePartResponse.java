package com.limoz.fleet.maintenance.inventory.dto;

import com.limoz.fleet.maintenance.inventory.domain.StockStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record SparePartResponse(
        Long id,
        String partNumber,
        String name,
        String category,
        String unit,
        BigDecimal unitCost,
        String supplier,
        int minimumStock,
        int currentStock,
        StockStatus stockStatus,
        BigDecimal stockValue,
        String location,
        boolean active,
        String notes,
        Instant createdAt,
        Instant updatedAt) {}
