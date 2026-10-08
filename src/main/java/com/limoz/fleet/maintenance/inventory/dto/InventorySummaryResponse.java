package com.limoz.fleet.maintenance.inventory.dto;

import java.math.BigDecimal;

/** KPI row of the spare parts screen, aggregated in the database. */
public record InventorySummaryResponse(long partCount, long lowStockCount, long outOfStockCount, BigDecimal stockValue) {}
