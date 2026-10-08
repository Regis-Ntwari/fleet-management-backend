package com.limoz.fleet.incident.dto;

import com.limoz.fleet.driver.dto.DriverSummary;
import com.limoz.fleet.incident.IncidentSeverity;
import com.limoz.fleet.incident.IncidentStatus;
import com.limoz.fleet.incident.IncidentType;
import com.limoz.fleet.vehicle.dto.VehicleSummary;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record IncidentResponse(
        Long id,
        String incidentNumber,
        VehicleSummary vehicle,
        DriverSummary driver,
        Long tripId,
        Instant occurredAt,
        String location,
        BigDecimal latitude,
        BigDecimal longitude,
        IncidentType incidentType,
        IncidentSeverity severity,
        String description,
        boolean thirdPartyInvolved,
        boolean injuries,
        String policeReportNumber,
        BigDecimal fuelLossLitres,
        BigDecimal estimatedCost,
        String investigationNotes,
        String correctiveAction,
        IncidentStatus status,
        Long reportedByUserId,
        Instant resolvedAt,
        Instant closedAt,
        List<IncidentUpdateResponse> updates,
        Instant createdAt,
        Instant updatedAt,
        String createdBy) {}
