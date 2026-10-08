package com.limoz.fleet.vehicle.timeline.service;

import com.limoz.fleet.common.util.Specifications;
import com.limoz.fleet.fuel.domain.FuelTransaction;
import com.limoz.fleet.fuel.repository.FuelTransactionRepository;
import com.limoz.fleet.incident.domain.Incident;
import com.limoz.fleet.incident.repository.IncidentRepository;
import com.limoz.fleet.maintenance.domain.MaintenanceRecord;
import com.limoz.fleet.maintenance.repository.MaintenanceRecordRepository;
import com.limoz.fleet.vehicle.timeline.domain.TimelineEntry;
import com.limoz.fleet.vehicle.timeline.domain.VehicleTimelineSource;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/** Fuel, maintenance and incident events on the vehicle activity timeline. */
@Configuration
@RequiredArgsConstructor
public class WorkshopTimelineSources {

    private final FuelTransactionRepository fuelRepository;
    private final MaintenanceRecordRepository maintenanceRepository;
    private final IncidentRepository incidentRepository;

    @Bean
    VehicleTimelineSource fuelTimelineSource() {
        return (vehicleId, from, to) -> {
            Specification<FuelTransaction> spec = Specifications.and(Specifications.equal("vehicle.id", vehicleId),
                    Specifications.isFalse("archived"), Specifications.instantBetween("transactionAt", from, to));
            return fuelRepository.findAll(spec, Sort.by("transactionAt")).stream()
                    .map(f -> new TimelineEntry(f.getTransactionAt(), "FUEL", "Refuelled " + f.getLitres().stripTrailingZeros().toPlainString() + " L",
                            f.getStationName() + " · " + f.getTotalAmount().stripTrailingZeros().toPlainString() + " RWF · " + f.getOdometerKm() + " km"
                                    + (f.isAnomaly() ? " · ANOMALY" : ""), "FuelTransaction", f.getId(), f.getReceiptNumber(), "/vehicles/" + vehicleId + "?tab=fuel"))
                    .toList();
        };
    }

    @Bean
    VehicleTimelineSource maintenanceTimelineSource() {
        return (vehicleId, from, to) -> {
            List<TimelineEntry> entries = new ArrayList<>();
            Specification<MaintenanceRecord> spec = Specifications.and(Specifications.equal("vehicle.id", vehicleId),
                    (root, q, cb) -> cb.or(cb.between(root.get("reportedAt"), from, to), cb.between(root.get("completedAt"), from, to)));
            for (MaintenanceRecord m : maintenanceRepository.findAll(spec, Sort.by("reportedAt"))) {
                if (!m.getReportedAt().isBefore(from) && !m.getReportedAt().isAfter(to)) {
                    entries.add(new TimelineEntry(m.getReportedAt(), "MAINTENANCE", "Maintenance reported", m.getMaintenanceNumber() + " · " + m.getComplaint(),
                            "MaintenanceRecord", m.getId(), m.getMaintenanceNumber(), "/maintenance/" + m.getId()));
                }
                if (m.getStartedAt() != null && !m.getStartedAt().isBefore(from) && !m.getStartedAt().isAfter(to)) {
                    entries.add(new TimelineEntry(m.getStartedAt(), "MAINTENANCE", "Entered workshop", m.getMaintenanceNumber(),
                            "MaintenanceRecord", m.getId(), m.getMaintenanceNumber(), "/maintenance/" + m.getId()));
                }
                if (m.getCompletedAt() != null && !m.getCompletedAt().isBefore(from) && !m.getCompletedAt().isAfter(to)) {
                    entries.add(new TimelineEntry(m.getCompletedAt(), "MAINTENANCE", "Maintenance completed",
                            m.getMaintenanceNumber() + " · " + m.getTotalCost().stripTrailingZeros().toPlainString() + " RWF",
                            "MaintenanceRecord", m.getId(), m.getMaintenanceNumber(), "/maintenance/" + m.getId()));
                }
            }
            return entries;
        };
    }

    @Bean
    VehicleTimelineSource incidentTimelineSource() {
        return (vehicleId, from, to) -> {
            Specification<Incident> spec = Specifications.and(Specifications.equal("vehicle.id", vehicleId), Specifications.instantBetween("occurredAt", from, to));
            return incidentRepository.findAll(spec, Sort.by("occurredAt")).stream()
                    .map(i -> new TimelineEntry(i.getOccurredAt(), "INCIDENT", i.getIncidentType().name().charAt(0) + i.getIncidentType().name().substring(1).toLowerCase().replace('_', ' ') + " reported",
                            i.getIncidentNumber() + " · " + i.getSeverity() + (i.getLocation() == null ? "" : " · " + i.getLocation()),
                            "Incident", i.getId(), i.getIncidentNumber(), "/incidents/" + i.getId()))
                    .toList();
        };
    }
}
