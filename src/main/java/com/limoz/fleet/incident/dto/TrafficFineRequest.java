package com.limoz.fleet.incident.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record TrafficFineRequest(
        @NotNull(message = "Vehicle is required") Long vehicleId,
        Long driverId,
        Long tripId,
        @Size(max = 60) String ticketReference,
        @NotNull Instant issuedAt,
        @Size(max = 255) String location,
        @NotBlank @Size(max = 255) String offence,
        @NotNull @DecimalMin(value = "0", inclusive = false, message = "Amount must be greater than zero") BigDecimal amount,
        @Size(max = 3) String currency,
        LocalDate dueDate,
        Boolean chargedToDriver,
        Long attachmentId,
        @Size(max = 500) String notes) {}
