package com.limoz.fleet.customer.dto;

import com.limoz.fleet.customer.domain.PurchaseOrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record PurchaseOrderResponse(
        Long id,
        String lpoNumber,
        CustomerSummary customer,
        CommitmentSummary commitment,
        Long bookingId,
        String bookingNumber,
        LocalDate issuedDate,
        LocalDate expiryDate,
        LocalDate receivedDate,
        BigDecimal value,
        String currency,
        PurchaseOrderStatus status,
        boolean expired,
        Long attachmentId,
        String notes,
        Instant createdAt,
        Instant updatedAt,
        String createdBy) {}
