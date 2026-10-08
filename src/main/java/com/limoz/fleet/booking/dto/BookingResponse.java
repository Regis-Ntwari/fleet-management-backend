package com.limoz.fleet.booking.dto;

import com.limoz.fleet.booking.BookingSource;
import com.limoz.fleet.booking.BookingStatus;
import com.limoz.fleet.booking.ServiceType;
import com.limoz.fleet.customer.dto.CustomerSummary;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Full booking detail: header, lines, deployment slots, extra charges and vouchers. */
public record BookingResponse(
        Long id,
        String bookingNumber,
        CustomerSummary customer,
        Long commitmentId,
        String commitmentReference,
        String contactName,
        String contactPhone,
        String contactEmail,
        ServiceType serviceType,
        String pickupLocation,
        String dropoffLocation,
        LocalDate startDate,
        LocalDate endDate,
        LocalTime pickupTime,
        LocalTime returnTime,
        Integer passengers,
        String currency,
        BigDecimal linesTotal,
        BigDecimal extraChargesTotal,
        BigDecimal totalAmount,
        BookingStatus status,
        BookingSource source,
        String cancellationReason,
        Instant confirmedAt,
        Instant deployedAt,
        Instant completedAt,
        Instant cancelledAt,
        String notes,
        int vehiclesRequested,
        int slotsAssigned,
        List<BookingLineResponse> lines,
        List<BookingSlotResponse> slots,
        List<ExtraChargeResponse> extraCharges,
        List<VoucherResponse> vouchers,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String updatedBy) {}
