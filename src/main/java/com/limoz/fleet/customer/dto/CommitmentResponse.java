package com.limoz.fleet.customer.dto;

import com.limoz.fleet.customer.domain.CommitmentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record CommitmentResponse(
        Long id,
        String reference,
        CustomerSummary customer,
        String title,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal contractedValue,
        String currency,
        BigDecimal consumedValue,
        BigDecimal remainingValue,
        BigDecimal utilisationPercent,
        long lpoCount,
        CommitmentStatus status,
        Long attachmentId,
        String notes,
        Instant createdAt,
        Instant updatedAt,
        String createdBy) {}
