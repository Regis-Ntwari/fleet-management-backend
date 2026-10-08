package com.limoz.fleet.maintenance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/** Payment against a completed job; the finance ledger entry is created by the finance module. */
public record MaintenancePaymentRequest(
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
        @Size(max = 30) String method,
        @Size(max = 60) String reference,
        Instant paidAt) {}
