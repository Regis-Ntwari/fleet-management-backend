package com.limoz.fleet.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RecentBooking(Long id, String bookingNumber, String customerName, String serviceType, LocalDate startDate,
                            LocalDate endDate, String status, BigDecimal totalAmount, String currency) {}
