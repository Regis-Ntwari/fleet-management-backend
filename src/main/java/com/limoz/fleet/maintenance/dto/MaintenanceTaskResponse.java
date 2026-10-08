package com.limoz.fleet.maintenance.dto;

import com.limoz.fleet.maintenance.TaskStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record MaintenanceTaskResponse(Long id, Long serviceTypeId, String serviceTypeName, String description, TaskStatus status,
                                      BigDecimal laborHours, BigDecimal laborCost, Instant completedAt, String completedBy,
                                      String notes, int sortOrder) {}
