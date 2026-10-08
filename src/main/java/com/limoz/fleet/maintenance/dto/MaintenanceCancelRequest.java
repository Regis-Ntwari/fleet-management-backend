package com.limoz.fleet.maintenance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MaintenanceCancelRequest(@NotBlank @Size(max = 255) String reason) {}
