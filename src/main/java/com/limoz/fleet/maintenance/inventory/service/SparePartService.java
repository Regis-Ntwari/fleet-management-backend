package com.limoz.fleet.maintenance.inventory.service;

import com.limoz.fleet.maintenance.inventory.domain.SparePart;
import com.limoz.fleet.maintenance.inventory.domain.StockStatus;
import com.limoz.fleet.maintenance.inventory.mapper.InventoryMapper;
import com.limoz.fleet.maintenance.inventory.repository.SparePartRepository;
import com.limoz.fleet.maintenance.inventory.repository.StockMovementRepository;

import com.limoz.fleet.audit.domain.AuditAction;
import com.limoz.fleet.audit.service.AuditService;
import com.limoz.fleet.common.api.PageResponse;
import com.limoz.fleet.common.exception.BusinessRuleException;
import com.limoz.fleet.common.exception.DuplicateResourceException;
import com.limoz.fleet.common.exception.ResourceNotFoundException;
import com.limoz.fleet.common.util.Specifications;
import com.limoz.fleet.config.CacheConfig;
import com.limoz.fleet.maintenance.inventory.dto.InventorySummaryResponse;
import com.limoz.fleet.maintenance.inventory.dto.SparePartFilter;
import com.limoz.fleet.maintenance.inventory.dto.SparePartRequest;
import com.limoz.fleet.maintenance.inventory.dto.SparePartResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Spare parts catalogue. Stock levels are never edited here; they move only through {@link StockMovementService}. */
@Service
@RequiredArgsConstructor
@Transactional
public class SparePartService {

    private final SparePartRepository repository;
    private final StockMovementRepository movementRepository;
    private final StockMovementService stockMovementService;
    private final InventoryMapper mapper;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<SparePartResponse> search(SparePartFilter f, Pageable pageable) {
        Specification<SparePart> spec = Specifications.and(
                f.active() == null ? Specifications.equal("active", true) : Specifications.equal("active", f.active()),
                Specifications.likeAny(f.q(), "name", "partNumber", "category", "supplier"),
                Specifications.equal("category", f.category()),
                Specifications.equal("supplier", f.supplier()),
                statusSpec(f.status()));
        return PageResponse.from(repository.findAll(spec, pageable).map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    public SparePartResponse get(Long id) {
        return mapper.toResponse(load(id));
    }

    @Transactional(readOnly = true)
    public SparePart load(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Spare part", id));
    }

    @Transactional(readOnly = true)
    public List<SparePartResponse> lowStock() {
        return repository.findLowStock().stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public InventorySummaryResponse summary() {
        SparePartRepository.InventoryTotals t = repository.totals();
        return new InventorySummaryResponse(t.getPartCount(), t.getLowStockCount(), t.getOutOfStockCount(), t.getStockValue());
    }

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public SparePartResponse create(SparePartRequest request) {
        String number = SparePart.normalisePartNumber(request.partNumber());
        if (repository.partNumberExists(number, null)) {
            throw new DuplicateResourceException("Spare part number " + number + " already exists");
        }
        SparePart part = new SparePart();
        apply(part, request);
        part.setPartNumber(number);
        part = repository.saveAndFlush(part);
        if (request.openingStock() != null && request.openingStock() > 0) {
            stockMovementService.openingStock(part, request.openingStock());
        }
        SparePartResponse response = mapper.toResponse(part);
        auditService.record(AuditAction.CREATE, "SparePart", part.getId(), number, null, response, "Spare part " + part.getName() + " catalogued");
        return response;
    }

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public SparePartResponse update(Long id, SparePartRequest request) {
        SparePart part = load(id);
        String number = SparePart.normalisePartNumber(request.partNumber());
        if (repository.partNumberExists(number, id)) {
            throw new DuplicateResourceException("Spare part number " + number + " already exists");
        }
        SparePartResponse before = mapper.toResponse(part);
        apply(part, request);
        part.setPartNumber(number);
        SparePartResponse after = mapper.toResponse(repository.save(part));
        auditService.record(AuditAction.UPDATE, "SparePart", id, number, before, after, "Spare part updated");
        return after;
    }

    /** Parts with ledger history are deactivated rather than deleted; unused parts are removed. */
    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public void deactivate(Long id) {
        SparePart part = load(id);
        SparePartResponse before = mapper.toResponse(part);
        if (movementRepository.countBySparePartId(id) == 0) {
            repository.delete(part);
            auditService.record(AuditAction.DELETE, "SparePart", id, part.getPartNumber(), before, null, "Spare part deleted");
            return;
        }
        if (!part.isActive()) {
            throw new BusinessRuleException("SPARE_PART_INACTIVE", "Spare part " + part.getPartNumber() + " is already inactive");
        }
        part.setActive(false);
        repository.save(part);
        auditService.record(AuditAction.DISABLE, "SparePart", id, part.getPartNumber(), before, mapper.toResponse(part), "Spare part deactivated");
    }

    private static Specification<SparePart> statusSpec(StockStatus status) {
        if (status == null) return null;
        return switch (status) {
            case OUT -> (root, q, cb) -> cb.lessThanOrEqualTo(root.get("currentStock"), 0);
            case LOW -> (root, q, cb) -> cb.and(cb.greaterThan(root.get("currentStock"), 0),
                    cb.lessThanOrEqualTo(root.get("currentStock"), root.get("minimumStock")));
            case OK -> (root, q, cb) -> cb.greaterThan(root.get("currentStock"), root.get("minimumStock"));
        };
    }

    private void apply(SparePart p, SparePartRequest r) {
        p.setName(r.name().trim());
        p.setCategory(blankToNull(r.category()));
        p.setUnit(r.unit() == null || r.unit().isBlank() ? "pcs" : r.unit().trim());
        p.setUnitCost(r.unitCost());
        p.setSupplier(blankToNull(r.supplier()));
        p.setMinimumStock(r.minimumStock() == null ? 0 : r.minimumStock());
        p.setLocation(blankToNull(r.location()));
        p.setActive(r.active() == null || r.active());
        p.setNotes(blankToNull(r.notes()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
