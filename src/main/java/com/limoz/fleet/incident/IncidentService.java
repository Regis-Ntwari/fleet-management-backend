package com.limoz.fleet.incident;

import com.limoz.fleet.audit.AuditAction;
import com.limoz.fleet.audit.AuditService;
import com.limoz.fleet.common.api.PageResponse;
import com.limoz.fleet.common.event.OperationalEvent;
import com.limoz.fleet.common.event.Severity;
import com.limoz.fleet.common.exception.BusinessRuleException;
import com.limoz.fleet.common.exception.InvalidStateTransitionException;
import com.limoz.fleet.common.exception.ResourceNotFoundException;
import com.limoz.fleet.common.sequence.ReferenceNumberService;
import com.limoz.fleet.common.sequence.ReferenceType;
import com.limoz.fleet.common.util.DateRanges;
import com.limoz.fleet.common.util.Specifications;
import com.limoz.fleet.config.CacheConfig;
import com.limoz.fleet.driver.Driver;
import com.limoz.fleet.driver.DriverService;
import com.limoz.fleet.incident.dto.IncidentFilter;
import com.limoz.fleet.incident.dto.IncidentRequest;
import com.limoz.fleet.incident.dto.IncidentResponse;
import com.limoz.fleet.incident.dto.IncidentSummaryResponse;
import com.limoz.fleet.incident.dto.IncidentUpdateResponse;
import com.limoz.fleet.security.AuthenticatedUser;
import com.limoz.fleet.security.Roles;
import com.limoz.fleet.security.SecurityUtils;
import com.limoz.fleet.vehicle.Vehicle;
import com.limoz.fleet.vehicle.VehicleService;
import com.limoz.fleet.vehicle.VehicleStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Incident and accident reports with a full investigation history. Every status change is appended to
 * the incident's update log (never deleted) and audited.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class IncidentService {

    private final IncidentRepository repository;
    private final IncidentUpdateRepository updateRepository;
    private final VehicleService vehicleService;
    private final DriverService driverService;
    private final IncidentMapper mapper;
    private final ReferenceNumberService referenceNumberService;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final ZoneId operationalZone;

    // ---------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public PageResponse<IncidentResponse> search(IncidentFilter f, Pageable pageable) {
        Instant from = f.from() == null ? null : f.from().atStartOfDay(operationalZone).toInstant();
        Instant to = f.to() == null ? null : f.to().plusDays(1).atStartOfDay(operationalZone).toInstant().minusMillis(1);
        Specification<Incident> spec = Specifications.and(
                matches(f.q()),
                Specifications.equal("vehicle.id", f.vehicleId()),
                Specifications.equal("driver.id", f.driverId()),
                Specifications.equal("incidentType", f.incidentType()),
                Specifications.equal("severity", f.severity()),
                Specifications.in("status", f.status()),
                Specifications.instantBetween("occurredAt", from, to));
        return PageResponse.from(repository.findAll(spec, pageable).map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    public IncidentResponse get(Long id) {
        Incident incident = load(id);
        return mapper.toResponse(incident, updateRepository.findByIncidentIdOrderByCreatedAtAscIdAsc(id));
    }

    @Transactional(readOnly = true)
    public List<IncidentUpdateResponse> history(Long id) {
        load(id);
        return updateRepository.findByIncidentIdOrderByCreatedAtAscIdAsc(id).stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Incident load(Long id) {
        return repository.findDetailedById(id).orElseThrow(() -> new ResourceNotFoundException("Incident", id));
    }

    @Transactional(readOnly = true)
    public PageResponse<IncidentResponse> forVehicle(Long vehicleId, Pageable pageable) {
        vehicleService.load(vehicleId);
        return PageResponse.from(repository.findByVehicleIdOrderByOccurredAtDesc(vehicleId, pageable).map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<IncidentResponse> forDriver(Long driverId, Pageable pageable) {
        driverService.load(driverId);
        return PageResponse.from(repository.findByDriverIdOrderByOccurredAtDesc(driverId, pageable).map(mapper::toResponse));
    }

    /** Counts for the Accidents screen; defaults to the last 12 months. */
    @Transactional(readOnly = true)
    public IncidentSummaryResponse summary(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.now(clock) : to;
        LocalDate start = from == null ? end.minusYears(1).plusDays(1) : from;
        DateRanges.InstantRange range = DateRanges.between(start, end, operationalZone);
        Map<IncidentType, Long> byType = new EnumMap<>(IncidentType.class);
        repository.countByType(range.from(), range.to()).forEach(c -> byType.put(c.getKey(), c.getTotal()));
        Map<IncidentSeverity, Long> bySeverity = new EnumMap<>(IncidentSeverity.class);
        repository.countBySeverity(range.from(), range.to()).forEach(c -> bySeverity.put(c.getKey(), c.getTotal()));
        Map<IncidentStatus, Long> byStatus = new EnumMap<>(IncidentStatus.class);
        repository.countByStatus(range.from(), range.to()).forEach(c -> byStatus.put(c.getKey(), c.getTotal()));
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        long open = byStatus.getOrDefault(IncidentStatus.OPEN, 0L) + byStatus.getOrDefault(IncidentStatus.UNDER_INVESTIGATION, 0L);
        long serious = bySeverity.getOrDefault(IncidentSeverity.MAJOR, 0L) + bySeverity.getOrDefault(IncidentSeverity.CRITICAL, 0L);
        Pageable top = PageRequest.of(0, 5);
        List<IncidentSummaryResponse.CountEntry> vehicles = repository.topVehicles(range.from(), range.to(), top).stream()
                .map(c -> new IncidentSummaryResponse.CountEntry(c.getId(), c.getLabel(), c.getTotal())).toList();
        List<IncidentSummaryResponse.CountEntry> drivers = repository.topDrivers(range.from(), range.to(), top).stream()
                .map(c -> new IncidentSummaryResponse.CountEntry(c.getId(), c.getLabel(), c.getTotal())).toList();
        return new IncidentSummaryResponse(start, end, total, open, serious, byStatus.getOrDefault(IncidentStatus.CLOSED, 0L),
                byType, bySeverity, byStatus, vehicles, drivers);
    }

    // ---------------------------------------------------------------- commands

    /**
     * Files a report. The driver defaults to the vehicle's current driver; a MAJOR / CRITICAL accident takes
     * the vehicle out of service (unless it is on a trip, where the trip workflow owns the status).
     */
    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public IncidentResponse report(IncidentRequest request) {
        Vehicle vehicle = vehicleService.loadActive(request.vehicleId());
        AuthenticatedUser reporter = SecurityUtils.requireCurrentUser();
        Incident incident = new Incident();
        incident.setIncidentNumber(referenceNumberService.next(ReferenceType.INCIDENT));
        incident.setVehicle(vehicle);
        incident.setReportedByUserId(reporter.id());
        apply(incident, request, true);
        incident = repository.save(incident);
        addUpdate(incident, IncidentUpdateType.NOTE, null, null,
                "Incident reported by " + reporter.fullName() + ": " + incident.getDescription());

        IncidentResponse response = mapper.toResponse(incident, updateRepository.findByIncidentIdOrderByCreatedAtAscIdAsc(incident.getId()));
        auditService.record(AuditAction.CREATE, "Incident", incident.getId(), incident.getIncidentNumber(), null, response,
                incident.getIncidentType() + " reported for " + vehicle.getPlateNumber());

        boolean critical = incident.getSeverity().isSerious() || incident.getIncidentType() == IncidentType.ACCIDENT;
        events.publishEvent(OperationalEvent.of("INCIDENT_CREATED", critical ? Severity.CRITICAL : Severity.WARNING,
                incident.getSeverity() + " " + incident.getIncidentType().name().toLowerCase().replace('_', ' ') + " reported - " + vehicle.getPlateNumber(),
                incident.getDescription() + (incident.getLocation() == null ? "" : " (" + incident.getLocation() + ")"),
                "Incident", incident.getId(), incident.getIncidentNumber(), "/incidents/" + incident.getId(),
                Roles.FLEET_MANAGER, Roles.MANAGEMENT, Roles.COMPLIANCE_OFFICER));

        if (incident.getIncidentType() == IncidentType.ACCIDENT && incident.getSeverity().isSerious()
                && vehicle.getOperationalStatus() != VehicleStatus.ON_TRIP) {
            vehicleService.transition(vehicle, VehicleStatus.OUT_OF_SERVICE,
                    incident.getSeverity() + " accident " + incident.getIncidentNumber());
        }
        return response;
    }

    public IncidentResponse update(Long id, IncidentRequest request) {
        Incident incident = load(id);
        if (incident.getStatus() == IncidentStatus.CLOSED) {
            throw new BusinessRuleException("INCIDENT_CLOSED", "Incident " + incident.getIncidentNumber() + " is closed; reopen it to edit");
        }
        IncidentResponse before = mapper.toResponse(incident);
        apply(incident, request, false);
        IncidentResponse after = mapper.toResponse(repository.save(incident));
        auditService.record(AuditAction.UPDATE, "Incident", id, incident.getIncidentNumber(), before, after, "Incident details updated");
        return after;
    }

    public IncidentUpdateResponse addNote(Long id, String note) {
        Incident incident = load(id);
        IncidentUpdate update = addUpdate(incident, IncidentUpdateType.NOTE, null, null, note);
        auditService.record(AuditAction.UPDATE, "Incident", id, incident.getIncidentNumber(), null, Map.of("note", note), "Investigation note added");
        return mapper.toResponse(update);
    }

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public IncidentResponse startInvestigation(Long id, String note) {
        Incident incident = load(id);
        if (incident.getStatus() != IncidentStatus.OPEN) {
            throw new InvalidStateTransitionException("Incident", incident.getStatus(), IncidentStatus.UNDER_INVESTIGATION);
        }
        transition(incident, IncidentStatus.UNDER_INVESTIGATION, note == null ? "Investigation started" : note);
        return detail(repository.save(incident));
    }

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public IncidentResponse resolve(Long id, String correctiveAction, String note) {
        Incident incident = load(id);
        if (!incident.getStatus().canTransitionTo(IncidentStatus.RESOLVED)) {
            throw new InvalidStateTransitionException("Incident", incident.getStatus(), IncidentStatus.RESOLVED);
        }
        incident.setCorrectiveAction(correctiveAction.trim());
        incident.setResolvedAt(Instant.now(clock));
        transition(incident, IncidentStatus.RESOLVED, note == null ? "Resolved: " + correctiveAction.trim() : note);
        return detail(repository.save(incident));
    }

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public IncidentResponse close(Long id, String note) {
        Incident incident = load(id);
        if (!incident.getStatus().canTransitionTo(IncidentStatus.CLOSED)) {
            throw new InvalidStateTransitionException("Incident", incident.getStatus(), IncidentStatus.CLOSED);
        }
        incident.setClosedAt(Instant.now(clock));
        transition(incident, IncidentStatus.CLOSED, note == null ? "Incident closed" : note);
        return detail(repository.save(incident));
    }

    /** RESOLVED / CLOSED -> UNDER_INVESTIGATION; a reason is mandatory. */
    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public IncidentResponse reopen(Long id, String note) {
        Incident incident = load(id);
        if (incident.getStatus().isOpen()) {
            throw new InvalidStateTransitionException("Incident", incident.getStatus(), IncidentStatus.UNDER_INVESTIGATION);
        }
        incident.setResolvedAt(null);
        incident.setClosedAt(null);
        transition(incident, IncidentStatus.UNDER_INVESTIGATION, "Reopened: " + note);
        return detail(repository.save(incident));
    }

    // ---------------------------------------------------------------- helpers

    private void transition(Incident incident, IncidentStatus to, String note) {
        IncidentStatus from = incident.getStatus();
        incident.setStatus(to);
        addUpdate(incident, IncidentUpdateType.STATUS_CHANGE, from, to, note);
        auditService.record(AuditAction.STATUS_CHANGE, "Incident", incident.getId(), incident.getIncidentNumber(),
                Map.of("status", from), Map.of("status", to), "Incident " + from + " -> " + to + ": " + note);
    }

    private IncidentUpdate addUpdate(Incident incident, IncidentUpdateType type, IncidentStatus from, IncidentStatus to, String note) {
        AuthenticatedUser user = SecurityUtils.currentUser().orElse(null);
        IncidentUpdate update = new IncidentUpdate();
        update.setIncident(incident);
        update.setUpdateType(type);
        update.setFromStatus(from);
        update.setToStatus(to);
        update.setNote(note);
        update.setUserId(user == null ? null : user.id());
        update.setAuthorName(user == null ? "system" : user.fullName());
        update.setCreatedAt(Instant.now(clock));
        return updateRepository.save(update);
    }

    private void apply(Incident i, IncidentRequest r, boolean creating) {
        if (r.occurredAt().isAfter(Instant.now(clock))) {
            throw new BusinessRuleException("OCCURRED_IN_FUTURE", "An incident cannot occur in the future");
        }
        if (r.tripId() != null && !repository.tripExists(r.tripId())) {
            throw new ResourceNotFoundException("Trip", r.tripId());
        }
        Driver driver;
        if (r.driverId() != null) {
            driver = driverService.loadActive(r.driverId());
        } else if (creating) {
            driver = i.getVehicle().getCurrentDriver();
        } else {
            driver = i.getDriver();
        }
        i.setDriver(driver);
        i.setTripId(r.tripId());
        i.setOccurredAt(r.occurredAt());
        i.setLocation(r.location());
        i.setLatitude(r.latitude());
        i.setLongitude(r.longitude());
        i.setIncidentType(r.incidentType());
        i.setSeverity(r.severity() == null ? IncidentSeverity.MINOR : r.severity());
        i.setDescription(r.description().trim());
        i.setThirdPartyInvolved(Boolean.TRUE.equals(r.thirdPartyInvolved()));
        i.setInjuries(Boolean.TRUE.equals(r.injuries()));
        i.setPoliceReportNumber(r.policeReportNumber());
        i.setFuelLossLitres(r.fuelLossLitres());
        i.setEstimatedCost(r.estimatedCost());
        i.setInvestigationNotes(r.investigationNotes());
    }

    private IncidentResponse detail(Incident incident) {
        return mapper.toResponse(incident, updateRepository.findByIncidentIdOrderByCreatedAtAscIdAsc(incident.getId()));
    }

    /** Free-text search over number, location, description, plate and driver name (left join - driver is optional). */
    private static Specification<Incident> matches(String q) {
        if (q == null || q.isBlank()) return null;
        String pattern = "%" + q.trim().toLowerCase() + "%";
        return (root, query, cb) -> {
            Join<Object, Object> driver = root.join("driver", JoinType.LEFT);
            return cb.or(
                    cb.like(cb.lower(root.get("incidentNumber")), pattern),
                    cb.like(cb.lower(root.get("location")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern),
                    cb.like(cb.lower(root.get("vehicle").get("plateNumber")), pattern),
                    cb.like(cb.lower(driver.get("firstName")), pattern),
                    cb.like(cb.lower(driver.get("lastName")), pattern));
        };
    }
}
