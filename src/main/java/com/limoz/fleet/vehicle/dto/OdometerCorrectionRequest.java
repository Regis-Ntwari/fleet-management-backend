package com.limoz.fleet.vehicle.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OdometerCorrectionRequest(@NotNull @Min(0) Long readingKm, @NotBlank @Size(max = 255) String reason) {}
