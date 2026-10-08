package com.limoz.fleet.fuel.dto;

import com.limoz.fleet.fuel.FuelPaymentMethod;
import com.limoz.fleet.vehicle.FuelType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public record FuelTransactionRequest(
        @NotNull Long vehicleId,
        Long driverId,
        Long tripId,
        Long bookingSlotId,
        Instant transactionAt,
        @NotBlank @Size(max = 120) String stationName,
        @Size(max = 120) String supplierName,
        FuelType fuelType,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 8, fraction = 2) BigDecimal litres,
        @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal pricePerLitre,
        @Size(min = 3, max = 3) String currency,
        @NotNull @Min(0) Long odometerKm,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal sensorDetectedLitres,
        Boolean fullTank,
        @Size(max = 60) String receiptNumber,
        Long receiptAttachmentId,
        FuelPaymentMethod paymentMethod,
        @Size(max = 500) String notes) {}
