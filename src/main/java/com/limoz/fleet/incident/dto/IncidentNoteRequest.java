package com.limoz.fleet.incident.dto;

import jakarta.validation.constraints.NotBlank;

public record IncidentNoteRequest(@NotBlank String note) {}
