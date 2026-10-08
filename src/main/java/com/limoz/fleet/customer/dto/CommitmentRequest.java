package com.limoz.fleet.customer.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CommitmentRequest(
        @NotNull(message = "Client is required") Long customerId,
        @NotBlank @Size(max = 150) String title,
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd,
        @NotNull @DecimalMin("0") BigDecimal contractedValue,
        @Size(max = 3) String currency,
        Long attachmentId,
        String notes) {}
