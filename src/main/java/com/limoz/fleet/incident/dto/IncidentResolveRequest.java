package com.limoz.fleet.incident.dto;

import jakarta.validation.constraints.NotBlank;

public record IncidentResolveRequest(@NotBlank(message = "Corrective action is required") String correctiveAction, String note) {}
