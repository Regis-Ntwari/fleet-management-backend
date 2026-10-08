package com.limoz.fleet.trip.dto;

import com.limoz.fleet.customer.dto.CustomerSummary;
import com.limoz.fleet.driver.dto.DriverSummary;
import com.limoz.fleet.trip.TripStatus;
import com.limoz.fleet.vehicle.dto.VehicleSummary;

import java.math.BigDecimal;
import java.time.Instant;

public record TripResponse(
        Long id,
        String tripNumber,
        VehicleSummary vehicle,
        DriverSummary driver,
        CustomerSummary customer,
        Long bookingId,
        String bookingNumber,
        Long bookingSlotId,
        String origin,
        String destination,
        String routeDescription,
        String purpose,
        String passengerDetails,
        Integer passengers,
        Instant scheduledStartAt,
        Instant scheduledEndAt,
        Instant startedAt,
        Instant endedAt,
        Long startOdometerKm,
        Long endOdometerKm,
        BigDecimal distanceKm,
        Integer durationMinutes,
        BigDecimal fuelUsedLitres,
        BigDecimal maxSpeedKph,
        TripStatus status,
        String cancellationReason,
        String notes,
        Instant createdAt,
        Instant updatedAt,
        String createdBy) {}
