package com.limoz.fleet.incident.dto;

import com.limoz.fleet.incident.domain.IncidentSeverity;
import com.limoz.fleet.incident.domain.IncidentType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/** Report / edit an incident. On update the vehicle is immutable (the report stays attached to the vehicle it was filed for). */
public record IncidentRequest(
        @NotNull(message = "Vehicle is required") Long vehicleId,
        Long driverId,
        Long tripId,
        @NotNull Instant occurredAt,
        @Size(max = 255) String location,
        BigDecimal latitude,
        BigDecimal longitude,
        @NotNull IncidentType incidentType,
        IncidentSeverity severity,
        @NotBlank(message = "Description is required") String description,
        Boolean thirdPartyInvolved,
        Boolean injuries,
        @Size(max = 60) String policeReportNumber,
        @DecimalMin("0") BigDecimal fuelLossLitres,
        @DecimalMin("0") BigDecimal estimatedCost,
        String investigationNotes) {}
