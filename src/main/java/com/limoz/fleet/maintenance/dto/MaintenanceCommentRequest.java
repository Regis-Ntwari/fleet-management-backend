package com.limoz.fleet.maintenance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MaintenanceCommentRequest(@NotBlank @Size(max = 2000) String body) {}
