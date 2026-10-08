package com.limoz.fleet.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExpenseRequest(
        @NotNull(message = "Category is required") Long categoryId,
        @NotBlank @Size(max = 255) String description,
        @NotNull @DecimalMin(value = "0", inclusive = false, message = "Amount must be greater than zero") BigDecimal amount,
        @Size(max = 3) String currency,
        @NotNull LocalDate incurredOn,
        Long vehicleId,
        Long driverId,
        Long tripId,
        Long bookingId,
        Long receiptAttachmentId,
        @Size(max = 500) String notes) {}
