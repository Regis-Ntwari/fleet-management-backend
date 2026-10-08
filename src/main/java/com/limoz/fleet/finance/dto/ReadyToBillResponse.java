package com.limoz.fleet.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** A completed booking waiting to be invoiced (the "Ready for Billing" tab). */
public record ReadyToBillResponse(Long bookingId, String bookingNumber, Long customerId, String customerName, LocalDate startDate,
                                  LocalDate endDate, Instant completedAt, long vehicleCount, String currency, BigDecimal totalAmount) {}
