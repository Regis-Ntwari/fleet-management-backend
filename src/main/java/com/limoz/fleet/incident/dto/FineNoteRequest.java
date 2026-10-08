package com.limoz.fleet.incident.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Reason recorded when a fine is disputed or waived (appended to the fine's notes). */
public record FineNoteRequest(@NotBlank @Size(max = 400) String note) {}
