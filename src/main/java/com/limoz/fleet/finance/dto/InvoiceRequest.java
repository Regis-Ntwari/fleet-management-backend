package com.limoz.fleet.finance.dto;

import com.limoz.fleet.finance.PaymentTerms;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record InvoiceRequest(
        @NotNull(message = "Client is required") Long customerId,
        Long bookingId,
        Long purchaseOrderId,
        LocalDate issueDate,
        LocalDate dueDate,
        PaymentTerms paymentTerms,
        @Size(max = 3) String currency,
        @DecimalMin("0") @DecimalMax("100") BigDecimal discountPercent,
        @NotEmpty(message = "At least one line is required") List<@Valid InvoiceLineRequest> lines,
        String notes) {}
