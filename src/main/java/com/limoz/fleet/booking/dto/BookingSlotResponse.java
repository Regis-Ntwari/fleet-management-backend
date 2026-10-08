package com.limoz.fleet.booking.dto;

import com.limoz.fleet.booking.domain.Shift;
import com.limoz.fleet.booking.domain.SlotStatus;
import com.limoz.fleet.driver.dto.DriverSummary;
import com.limoz.fleet.vehicle.dto.VehicleSummary;

import java.time.Instant;
import java.time.LocalDate;

/** One deployment slot with its booking context, used in booking detail and on the dispatcher board. */
public record BookingSlotResponse(
        Long id,
        Long bookingId,
        String bookingNumber,
        Long customerId,
        String customerName,
        Long lineId,
        int slotNumber,
        Long categoryId,
        String categoryName,
        String preferredModel,
        LocalDate startDate,
        LocalDate endDate,
        Shift shift,
        SlotStatus status,
        VehicleSummary vehicle,
        DriverSummary driver,
        Instant assignedAt,
        Long assignedByUserId,
        Instant departedAt,
        Instant returnedAt,
        Long odometerOut,
        Long odometerIn,
        Long tripId,
        String notes) {}
