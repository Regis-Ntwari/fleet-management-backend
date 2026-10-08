package com.limoz.fleet.maintenance.inventory;

import com.limoz.fleet.audit.AuditAction;
import com.limoz.fleet.audit.AuditService;
import com.limoz.fleet.common.api.PageResponse;
import com.limoz.fleet.common.event.OperationalEvent;
import com.limoz.fleet.common.event.Severity;
import com.limoz.fleet.common.exception.BusinessRuleException;
import com.limoz.fleet.common.exception.ResourceNotFoundException;
import com.limoz.fleet.common.sequence.ReferenceNumberService;
import com.limoz.fleet.common.sequence.ReferenceType;
import com.limoz.fleet.common.util.DateRanges;
import com.limoz.fleet.common.util.Specifications;
import com.limoz.fleet.config.CacheConfig;
import com.limoz.fleet.maintenance.MaintenanceRecord;
import com.limoz.fleet.maintenance.inventory.dto.StockMovementFilter;
import com.limoz.fleet.maintenance.inventory.dto.StockMovementRequest;
import com.limoz.fleet.maintenance.inventory.dto.StockMovementResponse;
import com.limoz.fleet.security.AuthenticatedUser;
import com.limoz.fleet.security.Roles;
import com.limoz.fleet.security.SecurityUtils;
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
import java.time.ZoneId;

/**
 * Stock ledger. Every movement locks its spare part row, validates the resulting balance and stores the
 * balance after the movement, so the ledger can always be reconciled against {@code current_stock}.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class StockMovementService {

    public static final String REFERENCE_PURCHASE_ORDER = "PURCHASE_ORDER";
    public static final String REFERENCE_MAINTENANCE = "MAINTENANCE";
    public static final String REFERENCE_OPENING_STOCK = "OPENING_STOCK";

    private final StockMovementRepository repository;
    private final SparePartRepository sparePartRepository;
    private final ReferenceNumberService referenceNumberService;
    private final InventoryMapper mapper;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final ZoneId operationalZone;

    // ---------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public PageResponse<StockMovementResponse> search(StockMovementFilter f, Pageable pageable) {
        Instant from = f.from() == null ? null : DateRanges.forDate(f.from(), operationalZone).from();
        Instant toExclusive = f.to() == null ? null : DateRanges.forDate(f.to(), operationalZone).to();
        Specification<StockMovement> spec = Specifications.and(
                Specifications.likeAny(f.q(), "sparePart.name", "sparePart.partNumber", "referenceNumber"),
                Specifications.equal("sparePart.id", f.sparePartId()),
                Specifications.equal("movementType", f.movementType()),
                Specifications.equal("maintenanceRecord.id", f.maintenanceRecordId()),
                Specifications.likeAny(f.reference(), "referenceNumber"),
                from == null ? null : (root, q, cb) -> cb.greaterThanOrEqualTo(root.get("movedAt"), from),
                toExclusive == null ? null : (root, q, cb) -> cb.lessThan(root.get("movedAt"), toExclusive));
        return PageResponse.from(repository.findAll(spec, pageable).map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    public StockMovementResponse get(Long id) {
        return mapper.toResponse(repository.findDetailedById(id).orElseThrow(() -> new ResourceNotFoundException("Stock movement", id)));
    }

    // ---------------------------------------------------------------- commands

    /** Manual movement from the Stock Movements screen. */
    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public StockMovementResponse record(StockMovementRequest request) {
        if (request.quantity() == 0) {
            throw new BusinessRuleException("INVALID_QUANTITY", "Quantity cannot be zero");
        }
        if (request.movementType() != StockMovementType.ADJUSTMENT && request.quantity() < 0) {
            throw new BusinessRuleException("INVALID_QUANTITY", "Quantity must be positive for " + request.movementType() + " movements");
        }
        SparePart part = lock(request.sparePartId());
        String referenceType = blankToNull(request.referenceType());
        String referenceNumber = blankToNull(request.referenceNumber());
        if (request.movementType() == StockMovementType.IN && referenceType == null && referenceNumber == null) {
            referenceType = REFERENCE_PURCHASE_ORDER;
            referenceNumber = referenceNumberService.next(ReferenceType.STOCK_PURCHASE);
        }
        StockMovement movement = apply(part, request.movementType(), request.quantity(), request.unitCost(), referenceType,
                referenceNumber, null, null, request.movedAt(), request.notes());
        return mapper.toResponse(movement);
    }

    /** Issues parts from stock for an approved maintenance part line; the MNT number is the ledger reference. */
    public StockMovement issueForMaintenance(Long sparePartId, int quantity, MaintenanceRecord record) {
        SparePart part = lock(sparePartId);
        return apply(part, StockMovementType.OUT, quantity, part.getUnitCost(), REFERENCE_MAINTENANCE,
                record.getMaintenanceNumber(), record.getId(), record, null, "Issued for " + record.getMaintenanceNumber());
    }

    /** Books the opening quantity of a newly catalogued part as an IN movement. */
    public StockMovement openingStock(SparePart part, int quantity) {
        return apply(part, StockMovementType.IN, quantity, part.getUnitCost(), REFERENCE_OPENING_STOCK, null, null, null, null,
                "Opening stock");
    }

    private StockMovement apply(SparePart part, StockMovementType type, int quantity, BigDecimal unitCost, String referenceType,
                                String referenceNumber, Long referenceId, MaintenanceRecord record, Instant movedAt, String notes) {
        int delta = type.signedQuantity(quantity);
        if (delta == 0) {
            throw new BusinessRuleException("INVALID_QUANTITY", "Quantity cannot be zero");
        }
        int balance = part.getCurrentStock() + delta;
        if (balance < 0) {
            throw new BusinessRuleException("INSUFFICIENT_STOCK", "Only " + part.getCurrentStock() + " " + part.getUnit() + " of "
                    + part.getName() + " (" + part.getPartNumber() + ") in stock; cannot issue " + Math.abs(delta));
        }
        StockStatus before = part.stockStatus();
        part.setCurrentStock(balance);
        sparePartRepository.save(part);

        AuthenticatedUser actor = SecurityUtils.currentUser().orElse(null);
        Instant now = Instant.now(clock);
        StockMovement movement = new StockMovement();
        movement.setSparePart(part);
        movement.setMovementType(type);
        movement.setQuantity(delta);
        movement.setUnitCost(unitCost == null ? part.getUnitCost() : unitCost);
        movement.setBalanceAfter(balance);
        movement.setReferenceType(referenceType);
        movement.setReferenceNumber(referenceNumber);
        movement.setReferenceId(referenceId);
        movement.setMaintenanceRecord(record);
        movement.setPerformedByUserId(actor == null ? null : actor.id());
        movement.setPerformedByName(actor == null ? "system" : actor.fullName());
        movement.setMovedAt(movedAt == null ? now : movedAt);
        movement.setNotes(blankToNull(notes));
        movement.setCreatedAt(now);
        movement.setCreatedBy(actor == null ? "system" : actor.email());
        movement = repository.save(movement);

        auditService.record(AuditAction.CREATE, "StockMovement", movement.getId(), part.getPartNumber(), null, mapper.toResponse(movement),
                type + " " + Math.abs(delta) + " " + part.getUnit() + " of " + part.getName() + " (balance " + balance + ")"
                        + (referenceNumber == null ? "" : " ref " + referenceNumber));
        StockStatus after = part.stockStatus();
        if (StockStatus.worsened(before, after) && after != StockStatus.OK) {
            events.publishEvent(OperationalEvent.of("LOW_STOCK", Severity.WARNING,
                    (after == StockStatus.OUT ? "Out of stock: " : "Low stock: ") + part.getName(),
                    part.getName() + " (" + part.getPartNumber() + ") is at " + balance + " " + part.getUnit()
                            + " (minimum " + part.getMinimumStock() + ")",
                    "SparePart", part.getId(), part.getPartNumber(), "/inventory/parts/" + part.getId(),
                    Roles.WORKSHOP_MANAGER, Roles.FLEET_MANAGER));
        }
        return movement;
    }

    private SparePart lock(Long id) {
        SparePart part = sparePartRepository.lockById(id).orElseThrow(() -> new ResourceNotFoundException("Spare part", id));
        if (!part.isActive()) {
            throw new BusinessRuleException("SPARE_PART_INACTIVE", "Spare part " + part.getPartNumber() + " is inactive");
        }
        return part;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
