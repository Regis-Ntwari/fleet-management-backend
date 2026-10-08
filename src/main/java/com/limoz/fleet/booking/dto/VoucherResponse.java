package com.limoz.fleet.booking.dto;

import com.limoz.fleet.booking.VoucherStatus;
import com.limoz.fleet.customer.dto.CustomerSummary;
import com.limoz.fleet.driver.dto.DriverSummary;
import com.limoz.fleet.vehicle.dto.VehicleSummary;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Deployment voucher data (order, vehicle and route, trip readings, billing split). */
public record VoucherResponse(
        Long id,
        String voucherNumber,
        Long slotId,
        int slotNumber,
        Long bookingId,
        String bookingNumber,
        CustomerSummary customer,
        Long purchaseOrderId,
        VehicleSummary vehicle,
        DriverSummary driver,
        Long accountManagerUserId,
        LocalDate voucherDate,
        String destination,
        String clientTel,
        String ownerName,
        String ownerDriverName,
        Long startKm,
        Long endKm,
        Long distanceKm,
        int plannedDays,
        BigDecimal effectiveDays,
        BigDecimal dayRate,
        BigDecimal institutionAmount,
        BigDecimal ownerAmount,
        BigDecimal fuelAmount,
        BigDecimal netAmount,
        BigDecimal missionDueAmount,
        BigDecimal poAmount,
        VoucherStatus status,
        String comment,
        String observation,
        Long tripId,
        Instant departedAt,
        Instant returnedAt,
        Instant createdAt,
        Instant updatedAt) {}
