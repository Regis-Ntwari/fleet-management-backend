package com.limoz.fleet.incident.dto;

import com.limoz.fleet.incident.domain.IncidentSeverity;
import com.limoz.fleet.incident.domain.IncidentStatus;
import com.limoz.fleet.incident.domain.IncidentType;

import java.time.LocalDate;
import java.util.List;

public record IncidentFilter(String q, Long vehicleId, Long driverId, IncidentType incidentType, IncidentSeverity severity,
                             List<IncidentStatus> status, LocalDate from, LocalDate to) {}
