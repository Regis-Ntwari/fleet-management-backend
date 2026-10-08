package com.limoz.fleet.booking.dto;

import com.limoz.fleet.booking.domain.BookingSource;
import com.limoz.fleet.booking.domain.BookingStatus;
import com.limoz.fleet.booking.domain.ServiceType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Booking list row: reference, client, dates, vehicles (assigned/total), status and total. */
public record BookingSummary(Long id, String bookingNumber, Long customerId, String customerName, Long commitmentId,
                             ServiceType serviceType, LocalDate startDate, LocalDate endDate, String currency,
                             BigDecimal totalAmount, BookingStatus status, BookingSource source, int vehiclesRequested,
                             int slotsAssigned, Instant createdAt, Instant updatedAt) {}
