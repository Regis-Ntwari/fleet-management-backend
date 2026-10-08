package com.limoz.fleet.finance.dto;

import com.limoz.fleet.finance.domain.PaymentDirection;
import com.limoz.fleet.finance.domain.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Ledger entry. At most one of invoiceId / maintenanceRecordId / expenseId / trafficFineId may be set;
 * the counterparty defaults to the linked record's party when omitted.
 */
public record PaymentRequest(
        @NotNull PaymentDirection direction,
        @Size(max = 150) String counterpartyName,
        Long customerId,
        Long invoiceId,
        Long maintenanceRecordId,
        Long expenseId,
        Long trafficFineId,
        @NotNull PaymentMethod method,
        @NotNull @DecimalMin(value = "0", inclusive = false, message = "Amount must be greater than zero") BigDecimal amount,
        @Size(max = 3) String currency,
        Instant paidAt,
        @Size(max = 80) String externalReference,
        Long receiptAttachmentId,
        @Size(max = 500) String notes) {}
