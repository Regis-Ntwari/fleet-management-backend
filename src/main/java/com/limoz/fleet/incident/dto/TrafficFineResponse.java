package com.limoz.fleet.incident.dto;

import com.limoz.fleet.driver.dto.DriverSummary;
import com.limoz.fleet.incident.FineStatus;
import com.limoz.fleet.vehicle.dto.VehicleSummary;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record TrafficFineResponse(
        Long id,
        String fineNumber,
        String ticketReference,
        VehicleSummary vehicle,
        DriverSummary driver,
        Long tripId,
        Instant issuedAt,
        String location,
        String offence,
        BigDecimal amount,
        String currency,
        LocalDate dueDate,
        FineStatus status,
        boolean overdue,
        Instant paidAt,
        Long paymentId,
        boolean chargedToDriver,
        Long attachmentId,
        String notes,
        Instant createdAt,
        Instant updatedAt,
        String createdBy) {}
