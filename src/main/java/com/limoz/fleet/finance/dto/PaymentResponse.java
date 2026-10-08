package com.limoz.fleet.finance.dto;

import com.limoz.fleet.finance.PaymentDirection;
import com.limoz.fleet.finance.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        Long id,
        String paymentNumber,
        PaymentDirection direction,
        String counterpartyName,
        Long customerId,
        String customerName,
        Long invoiceId,
        String invoiceNumber,
        Long maintenanceRecordId,
        Long expenseId,
        String expenseNumber,
        Long trafficFineId,
        PaymentMethod method,
        BigDecimal amount,
        String currency,
        Instant paidAt,
        String externalReference,
        Long receiptAttachmentId,
        Long recordedByUserId,
        String notes,
        boolean reversed,
        String reversalReason,
        Instant createdAt,
        String createdBy) {}
