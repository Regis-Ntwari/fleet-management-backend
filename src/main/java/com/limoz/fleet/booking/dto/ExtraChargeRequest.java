package com.limoz.fleet.booking.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ExtraChargeRequest(@NotBlank @Size(max = 255) String description, @NotNull @DecimalMin("0") BigDecimal amount) {}
