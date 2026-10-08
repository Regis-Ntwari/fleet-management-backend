package com.limoz.fleet.booking.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Editable voucher fields; omitted (null) fields are left unchanged. */
public record VoucherUpdateRequest(
        @DecimalMin("0") BigDecimal poAmount,
        Long purchaseOrderId,
        @Size(max = 500) String comment,
        String observation,
        @DecimalMin("0") BigDecimal ownerAmount) {}
