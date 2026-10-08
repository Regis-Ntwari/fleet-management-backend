package com.limoz.fleet.finance.dto;

import com.limoz.fleet.finance.PaymentTerms;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Options for generating a draft invoice from a completed booking; lines are pulled in automatically. */
public record InvoiceFromBookingRequest(
        PaymentTerms paymentTerms,
        @DecimalMin("0") @DecimalMax("100") BigDecimal discountPercent,
        LocalDate issueDate,
        String notes) {}
