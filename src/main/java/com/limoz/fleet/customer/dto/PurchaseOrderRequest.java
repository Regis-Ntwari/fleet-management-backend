package com.limoz.fleet.customer.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PurchaseOrderRequest(
        @NotNull(message = "Client is required") Long customerId,
        Long commitmentId,
        Long bookingId,
        @NotNull LocalDate issuedDate,
        LocalDate expiryDate,
        LocalDate receivedDate,
        @NotNull @DecimalMin("0") BigDecimal value,
        @Size(max = 3) String currency,
        Long attachmentId,
        @Size(max = 500) String notes) {}
