package com.limoz.fleet.finance.dto;

import com.limoz.fleet.finance.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Payment details when settling an expense or a traffic fine (the amount is the record's own amount). */
public record SettlementRequest(
        @NotNull PaymentMethod method,
        Instant paidAt,
        @Size(max = 150) String counterpartyName,
        @Size(max = 80) String externalReference,
        Long receiptAttachmentId,
        @Size(max = 500) String notes) {}
