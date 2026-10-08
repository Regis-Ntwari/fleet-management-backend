package com.limoz.fleet.dashboard.dto;

import java.time.Instant;
import java.time.LocalDate;

public record DeploymentToday(Long slotId, Long bookingId, String bookingNumber, String customerName, String plateNumber,
                              String categoryName, String driverName, String route, LocalDate startDate, LocalDate endDate,
                              String slotStatus, Instant departedAt) {}
