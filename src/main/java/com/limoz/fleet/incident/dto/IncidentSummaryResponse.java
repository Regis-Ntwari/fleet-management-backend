package com.limoz.fleet.incident.dto;

import com.limoz.fleet.incident.IncidentSeverity;
import com.limoz.fleet.incident.IncidentStatus;
import com.limoz.fleet.incident.IncidentType;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Accidents screen KPIs for a period: counts by type / severity / status and the most affected vehicles and drivers. */
public record IncidentSummaryResponse(
        LocalDate from,
        LocalDate to,
        long total,
        long open,
        long serious,
        long closed,
        Map<IncidentType, Long> byType,
        Map<IncidentSeverity, Long> bySeverity,
        Map<IncidentStatus, Long> byStatus,
        List<CountEntry> topVehicles,
        List<CountEntry> topDrivers) {

    public record CountEntry(Long id, String label, long count) {}
}
