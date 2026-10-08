package com.limoz.fleet.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record InvoiceLineRequest(
        @NotBlank @Size(max = 255) String description,
        @NotNull @DecimalMin(value = "0", inclusive = false, message = "Quantity must be greater than zero") BigDecimal quantity,
        @NotNull @DecimalMin("0") BigDecimal unitPrice,
        @DecimalMin("0") BigDecimal taxPercent) {}
