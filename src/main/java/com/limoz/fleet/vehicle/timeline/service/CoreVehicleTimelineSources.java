package com.limoz.fleet.vehicle.timeline.service;

import com.limoz.fleet.vehicle.timeline.domain.TimelineEntry;
import com.limoz.fleet.vehicle.timeline.domain.VehicleTimelineSource;

import com.limoz.fleet.assignment.domain.VehicleAssignment;
import com.limoz.fleet.assignment.repository.VehicleAssignmentRepository;
import com.limoz.fleet.audit.domain.AuditAction;
import com.limoz.fleet.audit.domain.AuditLog;
import com.limoz.fleet.audit.repository.AuditLogRepository;
import com.limoz.fleet.common.util.Specifications;
import com.limoz.fleet.document.domain.VehicleDocument;
import com.limoz.fleet.document.repository.VehicleDocumentRepository;
import com.limoz.fleet.vehicle.domain.OdometerLog;
import com.limoz.fleet.vehicle.repository.OdometerLogRepository;
import com.limoz.fleet.vehicle.domain.OdometerSource;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Timeline sources for assignments, odometer corrections, documents and audited status changes. */
@Configuration
@RequiredArgsConstructor
public class CoreVehicleTimelineSources {

    private final VehicleAssignmentRepository assignmentRepository;
    private final OdometerLogRepository odometerLogRepository;
    private final VehicleDocumentRepository vehicleDocumentRepository;
    private final AuditLogRepository auditLogRepository;

    @Bean
    VehicleTimelineSource assignmentTimelineSource() {
        return (vehicleId, from, to) -> {
            List<TimelineEntry> entries = new ArrayList<>();
            for (VehicleAssignment a : assignmentRepository.findByVehicleIdOrderByStartAtDesc(vehicleId, PageRequest.of(0, 200))) {
                if (!a.getStartAt().isBefore(from) && !a.getStartAt().isAfter(to)) {
                    entries.add(new TimelineEntry(a.getStartAt(), "ASSIGNMENT", "Driver assigned",
                            a.getDriver().getFullName() + (a.getPurpose() == null ? "" : " · " + a.getPurpose()),
                            "VehicleAssignment", a.getId(), null, "/drivers/" + a.getDriver().getId()));
                }
                if (a.getEndAt() != null && !a.getEndAt().isBefore(from) && !a.getEndAt().isAfter(to)) {
                    entries.add(new TimelineEntry(a.getEndAt(), "ASSIGNMENT", "Driver released",
                            a.getDriver().getFullName() + (a.getOdometerAtReturn() == null ? "" : " · odometer " + a.getOdometerAtReturn() + " km"),
                            "VehicleAssignment", a.getId(), null, "/drivers/" + a.getDriver().getId()));
                }
            }
            return entries;
        };
    }

    @Bean
    VehicleTimelineSource odometerTimelineSource() {
        return (vehicleId, from, to) -> odometerLogRepository
                .findByVehicleIdAndRecordedAtBetweenOrderByRecordedAtAsc(vehicleId, from, to).stream()
                .filter(l -> l.getSource() == OdometerSource.CORRECTION || l.getSource() == OdometerSource.MANUAL || l.getSource() == OdometerSource.IMPORT)
                .map(l -> new TimelineEntry(l.getRecordedAt(), "ODOMETER",
                        l.getSource() == OdometerSource.CORRECTION ? "Odometer corrected" : "Odometer recorded",
                        l.getReadingKm() + " km" + (l.getCorrectionReason() == null ? "" : " · " + l.getCorrectionReason()),
                        "OdometerLog", l.getId(), null, "/vehicles/" + vehicleId + "?tab=activity"))
                .toList();
    }

    @Bean
    VehicleTimelineSource documentTimelineSource() {
        return (vehicleId, from, to) -> vehicleDocumentRepository.findByVehicleIdOrderBySupersededAscExpiryDateDesc(vehicleId).stream()
                .filter(d -> d.getCreatedAt() != null && !d.getCreatedAt().isBefore(from) && !d.getCreatedAt().isAfter(to))
                .map(d -> new TimelineEntry(d.getCreatedAt(), "DOCUMENT", d.getDocumentType().getName() + " recorded",
                        (d.getDocumentNumber() == null ? "" : d.getDocumentNumber() + " · ") + "expires " + d.getExpiryDate(),
                        "VehicleDocument", d.getId(), d.getDocumentNumber(), "/vehicles/" + vehicleId + "?tab=documents"))
                .toList();
    }

    @Bean
    VehicleTimelineSource statusChangeTimelineSource() {
        return (vehicleId, from, to) -> {
            Specification<AuditLog> spec = Specifications.and(
                    Specifications.equal("entityType", "Vehicle"),
                    Specifications.equal("entityId", vehicleId),
                    Specifications.in("action", List.of(AuditAction.STATUS_CHANGE, AuditAction.ARCHIVE, AuditAction.RESTORE, AuditAction.CREATE)),
                    Specifications.instantBetween("occurredAt", from, to));
            return auditLogRepository.findAll(spec, PageRequest.of(0, 200, Sort.by("occurredAt").descending())).stream()
                    .map(a -> new TimelineEntry(a.getOccurredAt(), "STATUS",
                            a.getAction() == AuditAction.CREATE ? "Vehicle registered" : "Status changed",
                            a.getDescription() + (a.getUsername() == null ? "" : " · by " + a.getUsername()),
                            "AuditLog", a.getId(), null, "/vehicles/" + vehicleId))
                    .toList();
        };
    }
}
