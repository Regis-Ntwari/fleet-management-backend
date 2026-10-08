package com.limoz.fleet.maintenance.dto;

import com.limoz.fleet.maintenance.domain.TaskStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MaintenanceTaskStatusRequest(@NotNull TaskStatus status, @Size(max = 255) String notes) {}
