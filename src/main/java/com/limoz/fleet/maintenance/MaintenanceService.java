package com.limoz.fleet.maintenance;

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
import com.limoz.fleet.customer.Customer;
import com.limoz.fleet.customer.CustomerService;
import com.limoz.fleet.driver.Driver;
import com.limoz.fleet.driver.DriverService;
import com.limoz.fleet.maintenance.dto.GarageDashboardResponse;
import com.limoz.fleet.maintenance.dto.MaintenanceCancelRequest;
import com.limoz.fleet.maintenance.dto.MaintenanceCommentRequest;
import com.limoz.fleet.maintenance.dto.MaintenanceCompleteRequest;
import com.limoz.fleet.maintenance.dto.MaintenanceDetailResponse;
import com.limoz.fleet.maintenance.dto.MaintenanceFilter;
import com.limoz.fleet.maintenance.dto.MaintenancePartRequest;
import com.limoz.fleet.maintenance.dto.MaintenancePaymentRequest;
import com.limoz.fleet.maintenance.dto.MaintenanceRequest;
import com.limoz.fleet.maintenance.dto.MaintenanceResponse;
import com.limoz.fleet.maintenance.dto.MaintenanceReviewRequest;
import com.limoz.fleet.maintenance.dto.MaintenanceSummaryResponse;
import com.limoz.fleet.maintenance.dto.MaintenanceTaskRequest;
import com.limoz.fleet.maintenance.dto.MaintenanceTaskResponse;
import com.limoz.fleet.maintenance.dto.MaintenanceTaskStatusRequest;
import com.limoz.fleet.maintenance.dto.PartRejectRequest;
import com.limoz.fleet.maintenance.inventory.SparePart;
import com.limoz.fleet.maintenance.inventory.SparePartService;
import com.limoz.fleet.maintenance.inventory.StockMovement;
import com.limoz.fleet.maintenance.inventory.StockMovementService;
import com.limoz.fleet.security.AuthenticatedUser;
import com.limoz.fleet.security.Roles;
import com.limoz.fleet.security.SecurityUtils;
import com.limoz.fleet.user.User;
import com.limoz.fleet.user.UserService;
import com.limoz.fleet.vehicle.MaintenanceStatus;
import com.limoz.fleet.vehicle.OdometerService;
import com.limoz.fleet.vehicle.OdometerSource;
import com.limoz.fleet.vehicle.Vehicle;
import com.limoz.fleet.vehicle.VehicleService;
import com.limoz.fleet.vehicle.VehicleStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Maintenance job lifecycle shared by the simple MNT screens and the garage intake -> mechanic review ->
 * work progress -> gate pass flow. Every status change is journaled as a STATUS_CHANGE comment and audited.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class MaintenanceService {

    private static final int TOP_VEHICLES = 5;
    private static final int DEFAULT_SUMMARY_DAYS = 30;

    private final MaintenanceRecordRepository recordRepository;
    private final MaintenancePartRepository partRepository;
    private final VehicleService vehicleService;
    private final DriverService driverService;
    private final CustomerService customerService;
    private final UserService userService;
    private final WorkshopService workshopService;
    private final ServiceTypeService serviceTypeService;
    private final SparePartService sparePartService;
    private final StockMovementService stockMovementService;
    private final MaintenanceScheduleService scheduleService;
    private final OdometerService odometerService;
    private final ReferenceNumberService referenceNumberService;
    private final MaintenanceMapper mapper;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final ZoneId operationalZone;

    // ---------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public PageResponse<MaintenanceResponse> search(MaintenanceFilter f, Pageable pageable) {
        Instant from = f.from() == null ? null : DateRanges.forDate(f.from(), operationalZone).from();
        Instant toExclusive = f.to() == null ? null : DateRanges.forDate(f.to(), operationalZone).to();
        Specification<MaintenanceRecord> spec = Specifications.and(
                Specifications.likeAny(f.q(), "maintenanceNumber", "intakeNumber", "vehicle.plateNumber", "complaint"),
                Specifications.equal("vehicle.id", f.vehicleId()),
                Specifications.in("status", f.status()),
                Specifications.equal("maintenanceType", f.type()),
                Specifications.equal("priority", f.priority()),
                Specifications.equal("workshop.id", f.workshopId()),
                Specifications.equal("workshop.workshopType", f.workshopType()),
                Specifications.equal("technicianUserId", f.technicianUserId()),
                Specifications.equal("paymentStatus", f.paymentStatus()),
                Boolean.TRUE.equals(f.intakeOnly()) ? (root, q, cb) -> cb.isNotNull(root.get("intakeNumber")) : null,
                from == null ? null : (root, q, cb) -> cb.greaterThanOrEqualTo(root.get("reportedAt"), from),
                toExclusive == null ? null : (root, q, cb) -> cb.lessThan(root.get("reportedAt"), toExclusive));
        return PageResponse.from(recordRepository.findAll(spec, pageable).map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<MaintenanceResponse> forVehicle(Long vehicleId, Pageable pageable) {
        vehicleService.load(vehicleId);
        return search(new MaintenanceFilter(null, vehicleId, null, null, null, null, null, null, null, null, null, null), pageable);
    }

    @Transactional(readOnly = true)
    public MaintenanceDetailResponse get(Long id) {
        return mapper.toDetail(load(id));
    }

    @Transactional(readOnly = true)
    public GarageDashboardResponse garageDashboard() {
        Map<MaintenanceRecordStatus, Long> counts = new EnumMap<>(MaintenanceRecordStatus.class);
        for (MaintenanceRecordStatus status : MaintenanceRecordStatus.values()) {
            counts.put(status, 0L);
        }
        recordRepository.countByStatus().forEach(c -> counts.put(c.getStatus(), c.getTotal()));
        List<GarageDashboardResponse.GarageVehicleRow> rows = recordRepository.findByStatusNotInOrderByReportedAtAsc(MaintenanceRecordStatus.TERMINAL)
                .stream().map(this::toRow).toList();
        YearMonth month = YearMonth.now(clock);
        DateRanges.InstantRange thisMonth = DateRanges.between(month.atDay(1), month.atEndOfMonth(), operationalZone);
        MaintenanceRecordRepository.CountAndCost completed = recordRepository.completedBetween(thisMonth.from(), thisMonth.to(),
                List.of(MaintenanceRecordStatus.COMPLETED, MaintenanceRecordStatus.RELEASED));
        long awaitingParts = partRepository.countByStatusAndRecordStatusIn(PartStatus.REQUESTED, MaintenanceRecordStatus.OPEN);
        BigDecimal averageDays = BigDecimal.valueOf(recordRepository.averageDaysInGarageOpen().doubleValue()).setScale(1, RoundingMode.HALF_UP);
        return new GarageDashboardResponse(counts, rows.size(), rows, awaitingParts, counts.get(MaintenanceRecordStatus.WAITING_FOR_PARTS),
                completed.getTotal(), completed.getCost(), averageDays);
    }

    @Transactional(readOnly = true)
    public MaintenanceSummaryResponse summary(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.now(clock) : to;
        LocalDate start = from == null ? end.minusDays(DEFAULT_SUMMARY_DAYS) : from;
        if (start.isAfter(end)) {
            throw new BusinessRuleException("INVALID_DATE_RANGE", "'from' must not be after 'to'");
        }
        DateRanges.InstantRange r = DateRanges.between(start, end, operationalZone);
        MaintenanceRecordStatus excluded = MaintenanceRecordStatus.CANCELLED;
        MaintenanceRecordRepository.PeriodTotals totals = recordRepository.totalsBetween(r.from(), r.to(), excluded);
        List<MaintenanceSummaryResponse.TypeCost> byType = recordRepository.costByType(r.from(), r.to(), excluded).stream()
                .map(t -> new MaintenanceSummaryResponse.TypeCost(t.getType(), t.getTotal(), t.getCost())).toList();
        List<MaintenanceSummaryResponse.VehicleCost> top = recordRepository.topVehiclesByCost(r.from(), r.to(), excluded, PageRequest.of(0, TOP_VEHICLES))
                .stream().map(v -> new MaintenanceSummaryResponse.VehicleCost(v.getVehicleId(), v.getPlateNumber(),
                        v.getMake() + " " + v.getModel() + " (" + v.getPlateNumber() + ")", v.getTotal(), v.getCost())).toList();
        BigDecimal averageDays = BigDecimal.valueOf(recordRepository.averageDaysInGarageBetween(r.from(), r.to()).doubleValue()).setScale(1, RoundingMode.HALF_UP);
        return new MaintenanceSummaryResponse(start, end, totals.getTotal(), totals.getCost(), totals.getLaborCost(), totals.getPartsCost(),
                totals.getOtherCost(), totals.getAmountPaid(), averageDays, byType, top);
    }

    // ---------------------------------------------------------------- creation & edit

    /** Simple "new maintenance job" screen: MNT number only. */
    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public MaintenanceDetailResponse report(MaintenanceRequest request) {
        return create(request, false);
    }

    /** Garage reception check-in: MNT number plus a GRG intake number. */
    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public MaintenanceDetailResponse intake(MaintenanceRequest request) {
        return create(request, true);
    }

    private MaintenanceDetailResponse create(MaintenanceRequest request, boolean intake) {
        Vehicle vehicle = vehicleService.loadActive(request.vehicleId());
        MaintenanceRecord m = new MaintenanceRecord();
        m.setVehicle(vehicle);
        m.setMaintenanceNumber(referenceNumberService.next(ReferenceType.MAINTENANCE));
        if (intake) {
            m.setIntakeNumber(referenceNumberService.next(ReferenceType.GARAGE_INTAKE));
        }
        m.setReportedAt(request.reportedAt() == null ? Instant.now(clock) : request.reportedAt());
        m.setReportedByUserId(SecurityUtils.currentUserId().orElse(null));
        apply(m, request);
        m = recordRepository.saveAndFlush(m);
        applyOdometer(m, request.odometerKm());
        addComment(m, CommentType.SYSTEM, null, MaintenanceRecordStatus.REPORTED,
                intake ? "Vehicle checked in at the garage as " + m.getIntakeNumber() : "Maintenance job reported");
        MaintenanceDetailResponse response = mapper.toDetail(m);
        auditService.record(AuditAction.CREATE, "MaintenanceRecord", m.getId(), m.getMaintenanceNumber(), null, response.job(),
                (intake ? "Garage intake " + m.getIntakeNumber() + " / " : "Maintenance job ") + m.getMaintenanceNumber()
                        + " created for " + vehicle.getPlateNumber());
        return response;
    }

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public MaintenanceDetailResponse update(Long id, MaintenanceRequest request) {
        MaintenanceRecord m = load(id);
        requireOpen(m);
        if (!m.getVehicle().getId().equals(request.vehicleId())) {
            throw new BusinessRuleException("MAINTENANCE_VEHICLE_IMMUTABLE", "The vehicle of a maintenance job cannot be changed");
        }
        MaintenanceResponse before = mapper.toResponse(m);
        if (request.reportedAt() != null) {
            m.setReportedAt(request.reportedAt());
        }
        apply(m, request);
        applyOdometer(m, request.odometerKm());
        MaintenanceResponse after = mapper.toResponse(recordRepository.save(m));
        auditService.record(AuditAction.UPDATE, "MaintenanceRecord", id, m.getMaintenanceNumber(), before, after, "Maintenance job updated");
        return mapper.toDetail(m);
    }

    // ---------------------------------------------------------------- lifecycle

    /** Mechanic review: REPORTED -> INSPECTION (an existing review may be edited while still in INSPECTION). */
    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public MaintenanceDetailResponse submitReview(Long id, MaintenanceReviewRequest request) {
        MaintenanceRecord m = load(id);
        MaintenanceResponse before = mapper.toResponse(m);
        if (m.getStatus() == MaintenanceRecordStatus.REPORTED) {
            transition(m, MaintenanceRecordStatus.INSPECTION, "Mechanic review submitted");
        } else if (m.getStatus() != MaintenanceRecordStatus.INSPECTION) {
            throw new InvalidStateTransitionException("Maintenance job " + m.getMaintenanceNumber(), m.getStatus(), MaintenanceRecordStatus.INSPECTION);
        }
        m.setDiagnosis(request.diagnosis().trim());
        m.setObservedFaults(joinFaults(request.observedFaults()));
        m.setRecommendedRepair(request.recommendedRepair().trim());
        m.setLabourNotes(blankToNull(request.labourNotes()));
        m.setReviewDate(request.reviewDate() == null ? LocalDate.now(clock) : request.reviewDate());
        if (request.expectedCompletionAt() != null) {
            m.setExpectedCompletionAt(request.expectedCompletionAt());
        }
        if (request.parts() != null) {
            request.parts().forEach(p -> addPartLine(m, p));
        }
        recomputeCosts(m);
        MaintenanceResponse after = mapper.toResponse(recordRepository.save(m));
        auditService.record(AuditAction.UPDATE, "MaintenanceRecord", id, m.getMaintenanceNumber(), before, after,
                "Mechanic review recorded" + (request.parts() == null || request.parts().isEmpty() ? "" : " with " + request.parts().size() + " part(s) requested"));
        return mapper.toDetail(m);
    }

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public MaintenanceDetailResponse approve(Long id) {
        MaintenanceRecord m = load(id);
        transition(m, MaintenanceRecordStatus.APPROVED, "Job approved");
        m.setApprovedByUserId(SecurityUtils.currentUserId().orElse(null));
        m.setApprovedAt(Instant.now(clock));
        if (m.getManagerUserId() == null) {
            m.setManagerUserId(m.getApprovedByUserId());
        }
        auditService.record(AuditAction.APPROVE, "MaintenanceRecord", id, m.getMaintenanceNumber(), null, mapper.toResponse(m),
                "Maintenance job " + m.getMaintenanceNumber() + " approved");
        return mapper.toDetail(recordRepository.save(m));
    }

    /** Puts the vehicle in the workshop: refused while it is on a trip or already in another open workshop job. */
    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public MaintenanceDetailResponse start(Long id) {
        MaintenanceRecord m = load(id);
        Vehicle vehicle = m.getVehicle();
        if (!m.getStatus().canTransitionTo(MaintenanceRecordStatus.IN_PROGRESS)) {
            throw new InvalidStateTransitionException("Maintenance job " + m.getMaintenanceNumber(), m.getStatus(), MaintenanceRecordStatus.IN_PROGRESS);
        }
        if (vehicle.getOperationalStatus() == VehicleStatus.ON_TRIP) {
            throw new BusinessRuleException("VEHICLE_ON_TRIP", "Vehicle " + vehicle.getPlateNumber() + " is on a trip; complete the trip before starting the work");
        }
        if (recordRepository.existsByVehicleIdAndStatusInAndIdNot(vehicle.getId(), MaintenanceRecordStatus.IN_WORKSHOP, m.getId())) {
            throw new BusinessRuleException("VEHICLE_ALREADY_IN_WORKSHOP", "Vehicle " + vehicle.getPlateNumber() + " already has a job in progress");
        }
        transition(m, MaintenanceRecordStatus.IN_PROGRESS, "Work started");
        m.setStartedAt(Instant.now(clock));
        vehicleService.transition(vehicle, VehicleStatus.IN_MAINTENANCE, "Maintenance job " + m.getMaintenanceNumber() + " started");
        vehicle.setMaintenanceStatus(MaintenanceStatus.IN_WORKSHOP);
        return mapper.toDetail(recordRepository.save(m));
    }

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public MaintenanceDetailResponse waitForParts(Long id, String note) {
        MaintenanceRecord m = load(id);
        transition(m, MaintenanceRecordStatus.WAITING_FOR_PARTS, note == null || note.isBlank() ? "Waiting for parts" : note.trim());
        return mapper.toDetail(recordRepository.save(m));
    }

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public MaintenanceDetailResponse resume(Long id, String note) {
        MaintenanceRecord m = load(id);
        if (m.getStatus() != MaintenanceRecordStatus.WAITING_FOR_PARTS) {
            throw new InvalidStateTransitionException("Maintenance job " + m.getMaintenanceNumber(), m.getStatus(), MaintenanceRecordStatus.IN_PROGRESS);
        }
        transition(m, MaintenanceRecordStatus.IN_PROGRESS, note == null || note.isBlank() ? "Parts received, work resumed" : note.trim());
        return mapper.toDetail(recordRepository.save(m));
    }

    /**
     * Work-done: all requested parts must have been decided; labour of DONE tasks is folded into the labour cost,
     * costs are recomputed, the vehicle leaves the workshop and preventive schedules of DONE tasks are reset.
     */
    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public MaintenanceDetailResponse complete(Long id, MaintenanceCompleteRequest request) {
        MaintenanceRecord m = load(id);
        if (!m.getStatus().canTransitionTo(MaintenanceRecordStatus.COMPLETED)) {
            throw new InvalidStateTransitionException("Maintenance job " + m.getMaintenanceNumber(), m.getStatus(), MaintenanceRecordStatus.COMPLETED);
        }
        long pending = m.getParts().stream().filter(p -> p.getStatus() == PartStatus.REQUESTED).count();
        if (pending > 0) {
            throw new BusinessRuleException("PARTS_PENDING_APPROVAL", pending + " requested part(s) must be approved or rejected before completion");
        }
        MaintenanceResponse before = mapper.toResponse(m);
        transition(m, MaintenanceRecordStatus.COMPLETED, "Work completed");
        Instant now = Instant.now(clock);
        m.setServicePerformed(request.servicePerformed().trim());
        if (request.laborCost() != null) {
            m.setLaborCost(request.laborCost());
        }
        if (request.otherCost() != null) {
            m.setOtherCost(request.otherCost());
        }
        BigDecimal taskLabour = m.getTasks().stream().map(MaintenanceTask::getLaborCost).reduce(BigDecimal.ZERO, BigDecimal::add);
        m.setLaborCost(m.getLaborCost().add(taskLabour).setScale(2, RoundingMode.HALF_UP));
        m.setCompletedAt(now);
        applyOdometer(m, request.odometerKm());
        recomputeCosts(m);

        Vehicle vehicle = m.getVehicle();
        if (vehicle.getOperationalStatus() == VehicleStatus.IN_MAINTENANCE) {
            vehicleService.transition(vehicle, vehicleService.restingStatus(vehicle), "Maintenance job " + m.getMaintenanceNumber() + " completed");
        }
        long odometer = m.getOdometerKm() == null ? vehicle.getOdometerKm() : Math.max(m.getOdometerKm(), vehicle.getOdometerKm());
        LocalDate today = LocalDate.now(clock);
        m.getTasks().stream()
                .filter(t -> t.getStatus() == TaskStatus.DONE && t.getServiceType() != null)
                .forEach(t -> scheduleService.recordServiceDone(vehicle, t.getServiceType(), m, odometer, today));
        scheduleService.refreshVehicle(vehicle);

        MaintenanceResponse after = mapper.toResponse(recordRepository.save(m));
        auditService.record(AuditAction.COMPLETE, "MaintenanceRecord", id, m.getMaintenanceNumber(), before, after,
                "Maintenance job " + m.getMaintenanceNumber() + " completed, total cost " + m.getTotalCost());
        events.publishEvent(OperationalEvent.of("MAINTENANCE_COMPLETED", Severity.INFO,
                "Maintenance completed: " + vehicle.getPlateNumber(),
                m.getMaintenanceNumber() + " on " + vehicle.getDisplayName() + " completed; total cost " + m.getTotalCost() + " RWF",
                "MaintenanceRecord", m.getId(), m.getMaintenanceNumber(), "/maintenance/" + m.getId(),
                Roles.FLEET_MANAGER, Roles.MANAGEMENT, Roles.FINANCE));
        return mapper.toDetail(m);
    }

    /** Gate pass: COMPLETED -> RELEASED with a GP-<MNT number> pass. */
    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public MaintenanceDetailResponse release(Long id) {
        MaintenanceRecord m = load(id);
        transition(m, MaintenanceRecordStatus.RELEASED, "Gate pass issued");
        m.setGatePassNumber("GP-" + m.getMaintenanceNumber());
        m.setReleasedAt(Instant.now(clock));
        auditService.record(AuditAction.STATUS_CHANGE, "MaintenanceRecord", id, m.getMaintenanceNumber(), null, mapper.toResponse(m),
                "Gate pass " + m.getGatePassNumber() + " issued; vehicle released");
        return mapper.toDetail(recordRepository.save(m));
    }

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public MaintenanceDetailResponse cancel(Long id, MaintenanceCancelRequest request) {
        MaintenanceRecord m = load(id);
        boolean wasInWorkshop = m.getStatus().isInWorkshop();
        MaintenanceResponse before = mapper.toResponse(m);
        transition(m, MaintenanceRecordStatus.CANCELLED, "Cancelled: " + request.reason().trim());
        m.setCancellationReason(request.reason().trim());
        Vehicle vehicle = m.getVehicle();
        if (wasInWorkshop) {
            if (vehicle.getOperationalStatus() == VehicleStatus.IN_MAINTENANCE) {
                vehicleService.transition(vehicle, vehicleService.restingStatus(vehicle), "Maintenance job " + m.getMaintenanceNumber() + " cancelled");
            }
            scheduleService.refreshVehicle(vehicle);
        }
        MaintenanceResponse after = mapper.toResponse(recordRepository.save(m));
        auditService.record(AuditAction.CANCEL, "MaintenanceRecord", id, m.getMaintenanceNumber(), before, after,
                "Maintenance job " + m.getMaintenanceNumber() + " cancelled: " + request.reason());
        return mapper.toDetail(m);
    }

    // ---------------------------------------------------------------- parts

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public MaintenanceDetailResponse addPart(Long id, MaintenancePartRequest request) {
        MaintenanceRecord m = load(id);
        requireOpen(m);
        MaintenancePart part = addPartLine(m, request);
        recomputeCosts(m);
        recordRepository.saveAndFlush(m);
        auditService.record(AuditAction.CREATE, "MaintenancePart", part.getId(), m.getMaintenanceNumber(), null, mapper.toResponse(part),
                "Part requested on " + m.getMaintenanceNumber() + ": " + part.getQuantity() + " x " + part.getPartName());
        return mapper.toDetail(m);
    }

    /** Approves a requested part; catalogue parts are issued from stock (OUT movement) and become ISSUED. */
    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public MaintenanceDetailResponse approvePart(Long id, Long partId) {
        MaintenanceRecord m = load(id);
        requireOpen(m);
        MaintenancePart part = findPart(m, partId);
        requireRequested(part);
        if (part.getSparePart() != null) {
            StockMovement movement = stockMovementService.issueForMaintenance(part.getSparePart().getId(), part.getQuantity(), m);
            part.setStockMovementId(movement.getId());
            part.setStatus(PartStatus.ISSUED);
        } else {
            part.setStatus(PartStatus.APPROVED);
        }
        part.setApprovedByUserId(SecurityUtils.currentUserId().orElse(null));
        part.setApprovedAt(Instant.now(clock));
        recomputeCosts(m);
        addComment(m, CommentType.PART_DECISION, null, null, part.getQuantity() + " x " + part.getPartName()
                + (part.getStatus() == PartStatus.ISSUED ? " approved and issued from stock" : " approved"));
        recordRepository.save(m);
        auditService.record(AuditAction.APPROVE, "MaintenancePart", part.getId(), m.getMaintenanceNumber(), null, mapper.toResponse(part),
                "Part " + part.getPartName() + " " + part.getStatus().name().toLowerCase() + " on " + m.getMaintenanceNumber());
        return mapper.toDetail(m);
    }

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public MaintenanceDetailResponse rejectPart(Long id, Long partId, PartRejectRequest request) {
        MaintenanceRecord m = load(id);
        requireOpen(m);
        MaintenancePart part = findPart(m, partId);
        requireRequested(part);
        part.setStatus(PartStatus.REJECTED);
        part.setRejectionReason(request.reason().trim());
        part.setApprovedByUserId(SecurityUtils.currentUserId().orElse(null));
        part.setApprovedAt(Instant.now(clock));
        recomputeCosts(m);
        addComment(m, CommentType.PART_DECISION, null, null, part.getQuantity() + " x " + part.getPartName() + " rejected: " + request.reason().trim());
        recordRepository.save(m);
        auditService.record(AuditAction.REJECT, "MaintenancePart", part.getId(), m.getMaintenanceNumber(), null, mapper.toResponse(part),
                "Part " + part.getPartName() + " rejected on " + m.getMaintenanceNumber() + ": " + request.reason());
        return mapper.toDetail(m);
    }

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public MaintenanceDetailResponse removePart(Long id, Long partId) {
        MaintenanceRecord m = load(id);
        requireOpen(m);
        MaintenancePart part = findPart(m, partId);
        requireRequested(part);
        m.getParts().remove(part);
        recomputeCosts(m);
        recordRepository.save(m);
        auditService.record(AuditAction.DELETE, "MaintenancePart", partId, m.getMaintenanceNumber(), mapper.toResponse(part), null,
                "Requested part " + part.getPartName() + " removed from " + m.getMaintenanceNumber());
        return mapper.toDetail(m);
    }

    // ---------------------------------------------------------------- tasks

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public MaintenanceDetailResponse addTask(Long id, MaintenanceTaskRequest request) {
        MaintenanceRecord m = load(id);
        requireOpen(m);
        MaintenanceTask task = new MaintenanceTask();
        task.setRecord(m);
        task.setServiceType(request.serviceTypeId() == null ? null : serviceTypeService.loadActive(request.serviceTypeId()));
        task.setDescription(request.description().trim());
        task.setLaborHours(request.laborHours());
        task.setLaborCost(request.laborCost() == null ? BigDecimal.ZERO : request.laborCost());
        task.setNotes(blankToNull(request.notes()));
        task.setSortOrder(request.sortOrder() == null ? m.getTasks().size() + 1 : request.sortOrder());
        m.getTasks().add(task);
        recordRepository.saveAndFlush(m);
        auditService.record(AuditAction.CREATE, "MaintenanceTask", task.getId(), m.getMaintenanceNumber(), null, mapper.toResponse(task),
                "Task added to " + m.getMaintenanceNumber() + ": " + task.getDescription());
        return mapper.toDetail(m);
    }

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public MaintenanceDetailResponse setTaskStatus(Long id, Long taskId, MaintenanceTaskStatusRequest request) {
        MaintenanceRecord m = load(id);
        requireOpen(m);
        MaintenanceTask task = m.getTasks().stream().filter(t -> t.getId().equals(taskId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Maintenance task", taskId));
        MaintenanceTaskResponse before = mapper.toResponse(task);
        task.setStatus(request.status());
        if (request.status() == TaskStatus.PENDING) {
            task.setCompletedAt(null);
            task.setCompletedBy(null);
        } else {
            task.setCompletedAt(Instant.now(clock));
            task.setCompletedBy(currentActorName());
        }
        if (request.notes() != null) {
            task.setNotes(blankToNull(request.notes()));
        }
        recordRepository.save(m);
        auditService.record(AuditAction.UPDATE, "MaintenanceTask", taskId, m.getMaintenanceNumber(), before, mapper.toResponse(task),
                "Task '" + task.getDescription() + "' marked " + request.status() + " on " + m.getMaintenanceNumber());
        return mapper.toDetail(m);
    }

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public MaintenanceDetailResponse removeTask(Long id, Long taskId) {
        MaintenanceRecord m = load(id);
        requireOpen(m);
        MaintenanceTask task = m.getTasks().stream().filter(t -> t.getId().equals(taskId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Maintenance task", taskId));
        m.getTasks().remove(task);
        recordRepository.save(m);
        auditService.record(AuditAction.DELETE, "MaintenanceTask", taskId, m.getMaintenanceNumber(), mapper.toResponse(task), null,
                "Task '" + task.getDescription() + "' removed from " + m.getMaintenanceNumber());
        return mapper.toDetail(m);
    }

    // ---------------------------------------------------------------- comments & payments

    public MaintenanceDetailResponse addComment(Long id, MaintenanceCommentRequest request) {
        MaintenanceRecord m = load(id);
        MaintenanceComment comment = addComment(m, CommentType.NOTE, null, null, request.body().trim());
        recordRepository.saveAndFlush(m);
        auditService.record(AuditAction.UPDATE, "MaintenanceRecord", id, m.getMaintenanceNumber(), null, mapper.toResponse(comment),
                "Comment added to " + m.getMaintenanceNumber());
        return mapper.toDetail(m);
    }

    /**
     * Applies a payment to a completed job and derives UNPAID / PARTIAL / PAID. Exposed for the finance module,
     * which records the ledger entry and then calls this method.
     */
    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public MaintenanceResponse recordPayment(Long id, BigDecimal amount) {
        MaintenanceRecord m = load(id);
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessRuleException("INVALID_PAYMENT_AMOUNT", "Payment amount must be positive");
        }
        if (m.getStatus() != MaintenanceRecordStatus.COMPLETED && m.getStatus() != MaintenanceRecordStatus.RELEASED) {
            throw new BusinessRuleException("MAINTENANCE_NOT_COMPLETED", "Payments can only be recorded on completed jobs (current status " + m.getStatus() + ")");
        }
        BigDecimal paid = m.getAmountPaid().add(amount).setScale(2, RoundingMode.HALF_UP);
        if (paid.compareTo(m.getTotalCost()) > 0) {
            throw new BusinessRuleException("PAYMENT_EXCEEDS_TOTAL", "Payment of " + amount + " exceeds the outstanding balance of "
                    + m.getTotalCost().subtract(m.getAmountPaid()));
        }
        MaintenanceResponse before = mapper.toResponse(m);
        m.setAmountPaid(paid);
        m.setPaymentStatus(PaymentStatus.of(paid, m.getTotalCost()));
        MaintenanceResponse after = mapper.toResponse(recordRepository.save(m));
        auditService.record(AuditAction.UPDATE, "MaintenanceRecord", id, m.getMaintenanceNumber(), before, after,
                "Payment of " + amount + " recorded on " + m.getMaintenanceNumber() + " (" + m.getPaymentStatus() + ")");
        return after;
    }

    public MaintenanceDetailResponse recordPayment(Long id, MaintenancePaymentRequest request) {
        recordPayment(id, request.amount());
        MaintenanceRecord m = load(id);
        addComment(m, CommentType.SYSTEM, null, null, "Payment of " + request.amount() + " RWF recorded"
                + (request.method() == null || request.method().isBlank() ? "" : " via " + request.method().trim())
                + (request.reference() == null || request.reference().isBlank() ? "" : " (ref " + request.reference().trim() + ")")
                + (request.paidAt() == null ? "" : " on " + DateRanges.toLocalDate(request.paidAt(), operationalZone)));
        recordRepository.saveAndFlush(m);
        return mapper.toDetail(m);
    }

    // ---------------------------------------------------------------- helpers

    private MaintenanceRecord load(Long id) {
        return recordRepository.findDetailedById(id).orElseThrow(() -> new ResourceNotFoundException("Maintenance job", id));
    }

    private void apply(MaintenanceRecord m, MaintenanceRequest r) {
        Vehicle vehicle = m.getVehicle();
        Customer customer = r.customerId() == null ? null : customerService.loadActive(r.customerId());
        m.setCustomer(customer);
        m.setOwnerName(firstNonBlank(r.ownerName(), customer == null ? null : customer.getName(), vehicle.getOwnerName()));
        m.setDepartment(firstNonBlank(r.department(), vehicle.getDepartment()));
        Driver driver = r.driverId() == null ? null : driverService.loadActive(r.driverId());
        m.setDriver(driver);
        m.setDriverName(firstNonBlank(r.driverName(), driver == null ? null : driver.getFullName(),
                vehicle.getCurrentDriver() == null ? null : vehicle.getCurrentDriver().getFullName()));
        m.setDriverContact(firstNonBlank(r.driverContact(), driver == null ? null : driver.getPhone()));
        m.setComplaint(r.complaint().trim());
        m.setVisibleCondition(blankToNull(r.visibleCondition()));
        m.setMaintenanceType(r.maintenanceType() == null ? MaintenanceType.CORRECTIVE : r.maintenanceType());
        m.setPriority(r.priority() == null ? Priority.MEDIUM : r.priority());
        m.setWorkshop(r.workshopId() == null ? null : workshopService.loadActive(r.workshopId()));
        if (r.technicianUserId() != null) {
            User technician = userService.load(r.technicianUserId());
            m.setTechnicianUserId(technician.getId());
            m.setTechnicianName(firstNonBlank(r.technicianName(), technician.getFullName()));
        } else {
            m.setTechnicianUserId(null);
            m.setTechnicianName(blankToNull(r.technicianName()));
        }
        m.setManagerUserId(r.managerUserId() == null ? null : userService.load(r.managerUserId()).getId());
        m.setIncidentId(r.incidentId());
        m.setExpectedCompletionAt(r.expectedCompletionAt());
        m.setLaborCost(r.laborCost() == null ? BigDecimal.ZERO : r.laborCost());
        m.setOtherCost(r.otherCost() == null ? BigDecimal.ZERO : r.otherCost());
        m.setComments(blankToNull(r.comments()));
        recomputeCosts(m);
    }

    /** Stores the reading on the job and pushes it to the vehicle when it is not lower than the current reading. */
    private void applyOdometer(MaintenanceRecord m, Long odometerKm) {
        if (odometerKm == null) return;
        m.setOdometerKm(odometerKm);
        Vehicle vehicle = m.getVehicle();
        if (odometerKm >= vehicle.getOdometerKm()) {
            odometerService.record(vehicle, odometerKm, OdometerSource.MAINTENANCE, "MaintenanceRecord", m.getId());
        }
    }

    private MaintenancePart addPartLine(MaintenanceRecord m, MaintenancePartRequest r) {
        MaintenancePart part = new MaintenancePart();
        part.setRecord(m);
        BigDecimal unitCost = r.unitCost();
        if (r.sparePartId() != null) {
            SparePart catalogue = sparePartService.load(r.sparePartId());
            part.setSparePart(catalogue);
            part.setPartName(firstNonBlank(r.partName(), catalogue.getName()));
            part.setPartNumber(firstNonBlank(r.partNumber(), catalogue.getPartNumber()));
            if (unitCost == null) {
                unitCost = catalogue.getUnitCost();
            }
        } else {
            if (r.partName() == null || r.partName().isBlank()) {
                throw new BusinessRuleException("PART_NAME_REQUIRED", "A part name is required when the part is not from the catalogue");
            }
            part.setPartName(r.partName().trim());
            part.setPartNumber(blankToNull(r.partNumber()));
        }
        part.setQuantity(r.quantity());
        part.setUnitCost(unitCost == null ? BigDecimal.ZERO : unitCost);
        part.setLineTotal(part.getUnitCost().multiply(BigDecimal.valueOf(part.getQuantity())).setScale(2, RoundingMode.HALF_UP));
        part.setStatus(PartStatus.REQUESTED);
        part.setCreatedAt(Instant.now(clock));
        part.setCreatedBy(SecurityUtils.currentUsername().orElse("system"));
        m.getParts().add(part);
        return part;
    }

    /** parts_cost = approved / issued lines; total = labour + parts + other; payment status follows the new total. */
    private void recomputeCosts(MaintenanceRecord m) {
        BigDecimal parts = m.getParts().stream().filter(p -> p.getStatus().isCosted())
                .map(MaintenancePart::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        m.setPartsCost(parts.setScale(2, RoundingMode.HALF_UP));
        m.setTotalCost(m.getLaborCost().add(m.getPartsCost()).add(m.getOtherCost()).setScale(2, RoundingMode.HALF_UP));
        m.setPaymentStatus(PaymentStatus.of(m.getAmountPaid(), m.getTotalCost()));
    }

    private void transition(MaintenanceRecord m, MaintenanceRecordStatus to, String note) {
        MaintenanceRecordStatus from = m.getStatus();
        if (!from.canTransitionTo(to)) {
            throw new InvalidStateTransitionException("Maintenance job " + m.getMaintenanceNumber(), from, to);
        }
        m.setStatus(to);
        addComment(m, CommentType.STATUS_CHANGE, from, to, note);
        auditService.record(AuditAction.STATUS_CHANGE, "MaintenanceRecord", m.getId(), m.getMaintenanceNumber(),
                Map.of("status", from), Map.of("status", to),
                "Maintenance job " + m.getMaintenanceNumber() + " " + from + " -> " + to + (note == null ? "" : ": " + note));
    }

    private MaintenanceComment addComment(MaintenanceRecord m, CommentType type, MaintenanceRecordStatus from, MaintenanceRecordStatus to, String body) {
        AuthenticatedUser actor = SecurityUtils.currentUser().orElse(null);
        MaintenanceComment comment = new MaintenanceComment();
        comment.setRecord(m);
        comment.setUserId(actor == null ? null : actor.id());
        comment.setAuthorName(actor == null ? "system" : actor.fullName());
        comment.setAuthorRole(actor == null ? null : actor.roles().stream().sorted().findFirst().orElse(null));
        comment.setCommentType(type);
        comment.setFromStatus(from);
        comment.setToStatus(to);
        comment.setBody(body);
        comment.setCreatedAt(Instant.now(clock));
        m.getCommentEntries().add(comment);
        return comment;
    }

    private GarageDashboardResponse.GarageVehicleRow toRow(MaintenanceRecord m) {
        long days = mapper.daysInGarage(m);
        Vehicle v = m.getVehicle();
        return new GarageDashboardResponse.GarageVehicleRow(m.getId(), m.getMaintenanceNumber(), m.getIntakeNumber(), v.getId(), v.getPlateNumber(),
                v.getMake() + " " + v.getModel(), v.getCategory().getName(), m.getOwnerName(), m.getDepartment(), m.getReportedAt(),
                days, mapper.band(days), m.getTechnicianName(), m.getWorkshop() == null ? null : m.getWorkshop().getName(),
                m.getStatus(), m.getPriority());
    }

    private static void requireOpen(MaintenanceRecord m) {
        if (!m.getStatus().isOpen()) {
            throw new BusinessRuleException("MAINTENANCE_NOT_EDITABLE", "Maintenance job " + m.getMaintenanceNumber() + " is " + m.getStatus() + " and can no longer be edited");
        }
    }

    private static MaintenancePart findPart(MaintenanceRecord m, Long partId) {
        return m.getParts().stream().filter(p -> p.getId().equals(partId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Maintenance part", partId));
    }

    private static void requireRequested(MaintenancePart part) {
        if (part.getStatus() != PartStatus.REQUESTED) {
            throw new BusinessRuleException("PART_ALREADY_DECIDED", "Part " + part.getPartName() + " is already " + part.getStatus());
        }
    }

    private static String currentActorName() {
        return SecurityUtils.currentUser().map(AuthenticatedUser::fullName).orElse("system");
    }

    private static String joinFaults(List<String> faults) {
        if (faults == null) return null;
        String joined = faults.stream().filter(f -> f != null && !f.isBlank()).map(String::trim).reduce((a, b) -> a + "\n" + b).orElse("");
        return joined.isEmpty() ? null : joined;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
