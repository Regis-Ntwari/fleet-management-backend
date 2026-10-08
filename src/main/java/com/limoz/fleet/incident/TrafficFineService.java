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
import com.limoz.fleet.driver.DriverService;
import com.limoz.fleet.finance.Currencies;
import com.limoz.fleet.finance.PaymentDirection;
import com.limoz.fleet.finance.PaymentService;
import com.limoz.fleet.finance.dto.PaymentRequest;
import com.limoz.fleet.finance.dto.SettlementRequest;
import com.limoz.fleet.incident.dto.TrafficFineFilter;
import com.limoz.fleet.incident.dto.TrafficFineRequest;
import com.limoz.fleet.incident.dto.TrafficFineResponse;
import com.limoz.fleet.incident.dto.TrafficFineSummaryResponse;
import com.limoz.fleet.security.Roles;
import com.limoz.fleet.vehicle.Vehicle;
import com.limoz.fleet.vehicle.VehicleService;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.Map;

/** Traffic fines: UNPAID -> PAID (through the ledger) | DISPUTED -> PAID / WAIVED. */
@Service
@RequiredArgsConstructor
@Transactional
public class TrafficFineService {

    private final TrafficFineRepository repository;
    private final VehicleService vehicleService;
    private final DriverService driverService;
    private final PaymentService paymentService;
    private final IncidentMapper mapper;
    private final ReferenceNumberService referenceNumberService;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final ZoneId operationalZone;

    @Transactional(readOnly = true)
    public PageResponse<TrafficFineResponse> search(TrafficFineFilter f, Pageable pageable) {
        Instant from = f.from() == null ? null : f.from().atStartOfDay(operationalZone).toInstant();
        Instant to = f.to() == null ? null : f.to().plusDays(1).atStartOfDay(operationalZone).toInstant().minusMillis(1);
        Specification<TrafficFine> spec = Specifications.and(
                matches(f.q()),
                Specifications.equal("vehicle.id", f.vehicleId()),
                Specifications.equal("driver.id", f.driverId()),
                Specifications.in("status", f.status()),
                Specifications.instantBetween("issuedAt", from, to));
        return PageResponse.from(repository.findAll(spec, pageable).map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    public TrafficFineResponse get(Long id) {
        return mapper.toResponse(load(id));
    }

    @Transactional(readOnly = true)
    public TrafficFine load(Long id) {
        return repository.findDetailedById(id).orElseThrow(() -> new ResourceNotFoundException("Traffic fine", id));
    }

    @Transactional(readOnly = true)
    public PageResponse<TrafficFineResponse> forVehicle(Long vehicleId, Pageable pageable) {
        vehicleService.load(vehicleId);
        return PageResponse.from(repository.findByVehicleIdOrderByIssuedAtDesc(vehicleId, pageable).map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<TrafficFineResponse> forDriver(Long driverId, Pageable pageable) {
        driverService.load(driverId);
        return PageResponse.from(repository.findByDriverIdOrderByIssuedAtDesc(driverId, pageable).map(mapper::toResponse));
    }

    /** KPIs for fines issued in the period (default: last 12 months). */
    @Transactional(readOnly = true)
    public TrafficFineSummaryResponse summary(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.now(clock) : to;
        LocalDate start = from == null ? end.minusYears(1).plusDays(1) : from;
        DateRanges.InstantRange range = DateRanges.between(start, end, operationalZone);
        Map<FineStatus, Long> counts = new EnumMap<>(FineStatus.class);
        Map<FineStatus, BigDecimal> amounts = new EnumMap<>(FineStatus.class);
        repository.totalsByStatus(range.from(), range.to()).forEach(t -> {
            counts.put(t.getStatus(), t.getTotal());
            amounts.put(t.getStatus(), t.getAmount());
        });
        BigDecimal unpaid = amounts.getOrDefault(FineStatus.UNPAID, BigDecimal.ZERO);
        BigDecimal disputed = amounts.getOrDefault(FineStatus.DISPUTED, BigDecimal.ZERO);
        return new TrafficFineSummaryResponse(start, end, counts.values().stream().mapToLong(Long::longValue).sum(),
                counts.getOrDefault(FineStatus.UNPAID, 0L), unpaid, counts.getOrDefault(FineStatus.DISPUTED, 0L), disputed,
                counts.getOrDefault(FineStatus.PAID, 0L), amounts.getOrDefault(FineStatus.PAID, BigDecimal.ZERO),
                counts.getOrDefault(FineStatus.WAIVED, 0L), unpaid.add(disputed));
    }

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public TrafficFineResponse record(TrafficFineRequest request) {
        Vehicle vehicle = vehicleService.loadActive(request.vehicleId());
        TrafficFine fine = new TrafficFine();
        fine.setFineNumber(referenceNumberService.next(ReferenceType.TRAFFIC_FINE));
        fine.setVehicle(vehicle);
        fine.setDriver(request.driverId() == null ? vehicle.getCurrentDriver() : driverService.loadActive(request.driverId()));
        apply(fine, request);
        fine = repository.save(fine);
        TrafficFineResponse response = mapper.toResponse(fine);
        auditService.record(AuditAction.CREATE, "TrafficFine", fine.getId(), fine.getFineNumber(), null, response,
                "Fine recorded for " + vehicle.getPlateNumber() + ": " + fine.getOffence());
        events.publishEvent(OperationalEvent.of("FINE_RECORDED", Severity.WARNING,
                "Traffic fine logged - " + vehicle.getPlateNumber(),
                fine.getOffence() + " · " + fine.getAmount() + " " + fine.getCurrency()
                        + (fine.getDriver() == null ? "" : " · driver " + fine.getDriver().getFullName()),
                "TrafficFine", fine.getId(), fine.getFineNumber(), "/traffic-fines/" + fine.getId(), Roles.FINANCE, Roles.COMPLIANCE_OFFICER));
        return response;
    }

    public TrafficFineResponse update(Long id, TrafficFineRequest request) {
        TrafficFine fine = load(id);
        if (fine.getStatus() == FineStatus.PAID) {
            throw new BusinessRuleException("FINE_ALREADY_PAID", "Fine " + fine.getFineNumber() + " is paid and can no longer be edited");
        }
        TrafficFineResponse before = mapper.toResponse(fine);
        if (!fine.getVehicle().getId().equals(request.vehicleId())) {
            fine.setVehicle(vehicleService.loadActive(request.vehicleId()));
        }
        if (request.driverId() != null) {
            fine.setDriver(driverService.loadActive(request.driverId()));
        }
        apply(fine, request);
        TrafficFineResponse after = mapper.toResponse(repository.save(fine));
        auditService.record(AuditAction.UPDATE, "TrafficFine", id, fine.getFineNumber(), before, after, "Fine updated");
        return after;
    }

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public TrafficFineResponse dispute(Long id, String note) {
        TrafficFine fine = load(id);
        if (fine.getStatus() != FineStatus.UNPAID) {
            throw new InvalidStateTransitionException("Traffic fine", fine.getStatus(), FineStatus.DISPUTED);
        }
        appendNote(fine, "Disputed: " + note);
        transition(fine, FineStatus.DISPUTED, "Fine disputed: " + note);
        return mapper.toResponse(repository.save(fine));
    }

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public TrafficFineResponse waive(Long id, String note) {
        TrafficFine fine = load(id);
        if (!fine.getStatus().canTransitionTo(FineStatus.WAIVED)) {
            throw new InvalidStateTransitionException("Traffic fine", fine.getStatus(), FineStatus.WAIVED);
        }
        appendNote(fine, "Waived: " + note);
        transition(fine, FineStatus.WAIVED, "Fine waived: " + note);
        return mapper.toResponse(repository.save(fine));
    }

    /** UNPAID / DISPUTED -> PAID by recording an OUT payment; the ledger links the payment and stamps paid_at. */
    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public TrafficFineResponse pay(Long id, SettlementRequest request) {
        TrafficFine fine = load(id);
        if (!fine.getStatus().canTransitionTo(FineStatus.PAID)) {
            throw new InvalidStateTransitionException("Traffic fine", fine.getStatus(), FineStatus.PAID);
        }
        paymentService.record(new PaymentRequest(PaymentDirection.OUT, request.counterpartyName(), null, null, null, null, fine.getId(),
                request.method(), fine.getAmount(), fine.getCurrency(), request.paidAt(), request.externalReference(),
                request.receiptAttachmentId(), request.notes()));
        return mapper.toResponse(load(id));
    }

    private void transition(TrafficFine fine, FineStatus to, String description) {
        FineStatus from = fine.getStatus();
        fine.setStatus(to);
        auditService.record(AuditAction.STATUS_CHANGE, "TrafficFine", fine.getId(), fine.getFineNumber(),
                Map.of("status", from), Map.of("status", to), description);
    }

    private void appendNote(TrafficFine fine, String note) {
        String combined = fine.getNotes() == null || fine.getNotes().isBlank() ? note : fine.getNotes() + "\n" + note;
        fine.setNotes(combined.length() > 500 ? combined.substring(combined.length() - 500) : combined);
    }

    private void apply(TrafficFine f, TrafficFineRequest r) {
        if (r.issuedAt().isAfter(Instant.now(clock))) {
            throw new BusinessRuleException("ISSUED_IN_FUTURE", "Issue date cannot be in the future");
        }
        if (r.tripId() != null && !repository.tripExists(r.tripId())) {
            throw new ResourceNotFoundException("Trip", r.tripId());
        }
        f.setTicketReference(r.ticketReference());
        f.setTripId(r.tripId());
        f.setIssuedAt(r.issuedAt());
        f.setLocation(r.location());
        f.setOffence(r.offence().trim());
        f.setAmount(r.amount());
        f.setCurrency(Currencies.normalise(r.currency()));
        f.setDueDate(r.dueDate());
        f.setChargedToDriver(Boolean.TRUE.equals(r.chargedToDriver()));
        f.setAttachmentId(r.attachmentId());
        if (r.notes() != null) {
            f.setNotes(r.notes());
        }
    }

    private static Specification<TrafficFine> matches(String q) {
        if (q == null || q.isBlank()) return null;
        String pattern = "%" + q.trim().toLowerCase() + "%";
        return (root, query, cb) -> {
            Join<Object, Object> driver = root.join("driver", JoinType.LEFT);
            return cb.or(
                    cb.like(cb.lower(root.get("fineNumber")), pattern),
                    cb.like(cb.lower(root.get("offence")), pattern),
                    cb.like(cb.lower(root.get("ticketReference")), pattern),
                    cb.like(cb.lower(root.get("vehicle").get("plateNumber")), pattern),
                    cb.like(cb.lower(driver.get("firstName")), pattern),
                    cb.like(cb.lower(driver.get("lastName")), pattern));
        };
    }
}
