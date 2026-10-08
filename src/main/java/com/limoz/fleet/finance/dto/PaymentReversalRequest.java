package com.limoz.fleet.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PaymentReversalRequest(@NotBlank @Size(max = 255) String reason) {}
