package com.limoz.fleet.booking.dto;

import com.limoz.fleet.booking.VoucherStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/** "Record return" close-out of a deployed slot / voucher. Status defaults to RETURNED. */
public record ReturnRequest(
        @NotNull(message = "End odometer reading is required") @Min(0) Long odometerIn,
        Instant returnedAt,
        @DecimalMin("0") BigDecimal fuelAmount,
        @DecimalMin("0") BigDecimal missionDueAmount,
        @DecimalMin("0") BigDecimal ownerAmount,
        VoucherStatus status,
        @Size(max = 500) String comment,
        String observation) {}
