package com.limoz.fleet.maintenance.dto;

import com.limoz.fleet.maintenance.PartStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record MaintenancePartResponse(Long id, Long sparePartId, String partName, String partNumber, int quantity, BigDecimal unitCost,
                                      BigDecimal lineTotal, PartStatus status, Long approvedByUserId, Instant approvedAt,
                                      String rejectionReason, Long stockMovementId, Instant createdAt, String createdBy) {}
