package com.limoz.fleet.fuel.dto;

import com.limoz.fleet.driver.dto.DriverSummary;
import com.limoz.fleet.fuel.domain.FuelPaymentMethod;
import com.limoz.fleet.vehicle.domain.FuelType;
import com.limoz.fleet.vehicle.dto.VehicleSummary;

import java.math.BigDecimal;
import java.time.Instant;

public record FuelTransactionResponse(
        Long id,
        VehicleSummary vehicle,
        DriverSummary driver,
        Long tripId,
        Long bookingSlotId,
        Instant transactionAt,
        String stationName,
        String supplierName,
        FuelType fuelType,
        BigDecimal litres,
        BigDecimal pricePerLitre,
        BigDecimal totalAmount,
        String currency,
        long odometerKm,
        Long previousOdometerKm,
        BigDecimal distanceSinceLastKm,
        BigDecimal consumptionLPer100km,
        BigDecimal kmPerLitre,
        BigDecimal sensorDetectedLitres,
        BigDecimal varianceLitres,
        boolean anomaly,
        String anomalyReason,
        boolean fullTank,
        String receiptNumber,
        Long receiptAttachmentId,
        FuelPaymentMethod paymentMethod,
        Long enteredByUserId,
        String notes,
        boolean archived,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String updatedBy) {}
