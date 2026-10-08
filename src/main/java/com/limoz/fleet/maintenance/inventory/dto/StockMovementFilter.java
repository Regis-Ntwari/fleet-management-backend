package com.limoz.fleet.maintenance.inventory.dto;

import com.limoz.fleet.maintenance.inventory.StockMovementType;

import java.time.LocalDate;

/** Ledger filters; {@code q} matches part name, part number or reference number. */
public record StockMovementFilter(String q, Long sparePartId, StockMovementType movementType, LocalDate from, LocalDate to,
                                  String reference, Long maintenanceRecordId) {}
