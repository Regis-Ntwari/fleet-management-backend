package com.limoz.fleet.finance.dto;

import com.limoz.fleet.driver.dto.DriverSummary;
import com.limoz.fleet.finance.domain.ExpenseStatus;
import com.limoz.fleet.vehicle.dto.VehicleSummary;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record ExpenseResponse(
        Long id,
        String expenseNumber,
        ExpenseCategoryResponse category,
        String description,
        BigDecimal amount,
        String currency,
        LocalDate incurredOn,
        VehicleSummary vehicle,
        DriverSummary driver,
        Long tripId,
        Long bookingId,
        Long submittedByUserId,
        String submittedByName,
        ExpenseStatus status,
        Long approvedByUserId,
        Instant approvedAt,
        String rejectionReason,
        Long receiptAttachmentId,
        String notes,
        Instant createdAt,
        Instant updatedAt) {}
