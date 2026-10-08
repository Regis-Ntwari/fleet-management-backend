package com.limoz.fleet.maintenance;

import com.limoz.fleet.audit.AuditAction;
import com.limoz.fleet.audit.AuditService;
import com.limoz.fleet.common.exception.BusinessRuleException;
import com.limoz.fleet.common.exception.DuplicateResourceException;
import com.limoz.fleet.common.exception.ResourceNotFoundException;
import com.limoz.fleet.config.CacheConfig;
import com.limoz.fleet.maintenance.dto.WorkshopRequest;
import com.limoz.fleet.maintenance.dto.WorkshopResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class WorkshopService {

    private final WorkshopRepository repository;
    private final MaintenanceRecordRepository recordRepository;
    private final MaintenanceMapper mapper;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.REFERENCE_DATA, key = "'workshops'")
    public List<WorkshopResponse> list() {
        return repository.findAllByOrderByNameAsc().stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public WorkshopResponse get(Long id) {
        return mapper.toResponse(load(id));
    }

    @Transactional(readOnly = true)
    public Workshop load(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Workshop", id));
    }

    @Transactional(readOnly = true)
    public Workshop loadActive(Long id) {
        Workshop workshop = load(id);
        if (!workshop.isActive()) {
            throw new BusinessRuleException("WORKSHOP_INACTIVE", "Workshop " + workshop.getName() + " is inactive");
        }
        return workshop;
    }

    @CacheEvict(cacheNames = CacheConfig.REFERENCE_DATA, allEntries = true)
    public WorkshopResponse create(WorkshopRequest request) {
        if (repository.nameExists(request.name().trim(), null)) {
            throw new DuplicateResourceException("Workshop " + request.name() + " already exists");
        }
        Workshop workshop = new Workshop();
        apply(workshop, request);
        WorkshopResponse response = mapper.toResponse(repository.save(workshop));
        auditService.record(AuditAction.CREATE, "Workshop", workshop.getId(), workshop.getName(), null, response, "Workshop created");
        return response;
    }

    @CacheEvict(cacheNames = CacheConfig.REFERENCE_DATA, allEntries = true)
    public WorkshopResponse update(Long id, WorkshopRequest request) {
        Workshop workshop = load(id);
        if (repository.nameExists(request.name().trim(), id)) {
            throw new DuplicateResourceException("Workshop " + request.name() + " already exists");
        }
        WorkshopResponse before = mapper.toResponse(workshop);
        apply(workshop, request);
        WorkshopResponse after = mapper.toResponse(repository.save(workshop));
        auditService.record(AuditAction.UPDATE, "Workshop", id, workshop.getName(), before, after, "Workshop updated");
        return after;
    }

    /** Workshops referenced by jobs are deactivated instead of deleted. */
    @CacheEvict(cacheNames = CacheConfig.REFERENCE_DATA, allEntries = true)
    public void delete(Long id) {
        Workshop workshop = load(id);
        WorkshopResponse before = mapper.toResponse(workshop);
        if (recordRepository.countByWorkshopId(id) > 0) {
            workshop.setActive(false);
            repository.save(workshop);
            auditService.record(AuditAction.DISABLE, "Workshop", id, workshop.getName(), before, mapper.toResponse(workshop),
                    "Workshop deactivated (referenced by maintenance jobs)");
            return;
        }
        repository.delete(workshop);
        auditService.record(AuditAction.DELETE, "Workshop", id, workshop.getName(), before, null, "Workshop deleted");
    }

    private void apply(Workshop w, WorkshopRequest r) {
        w.setName(r.name().trim());
        w.setWorkshopType(r.workshopType());
        w.setContactName(blankToNull(r.contactName()));
        w.setPhone(blankToNull(r.phone()));
        w.setEmail(r.email() == null || r.email().isBlank() ? null : r.email().trim().toLowerCase());
        w.setAddress(blankToNull(r.address()));
        w.setActive(r.active() == null || r.active());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
