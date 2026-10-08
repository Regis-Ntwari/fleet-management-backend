package com.limoz.fleet.maintenance.service;

import com.limoz.fleet.maintenance.domain.ServiceCategory;
import com.limoz.fleet.maintenance.domain.ServiceType;
import com.limoz.fleet.maintenance.mapper.MaintenanceMapper;
import com.limoz.fleet.maintenance.repository.MaintenanceScheduleRepository;
import com.limoz.fleet.maintenance.repository.MaintenanceTaskRepository;
import com.limoz.fleet.maintenance.repository.ServiceTypeRepository;

import com.limoz.fleet.audit.domain.AuditAction;
import com.limoz.fleet.audit.service.AuditService;
import com.limoz.fleet.common.exception.BusinessRuleException;
import com.limoz.fleet.common.exception.DuplicateResourceException;
import com.limoz.fleet.common.exception.ResourceNotFoundException;
import com.limoz.fleet.config.CacheConfig;
import com.limoz.fleet.maintenance.dto.ServiceTypeRequest;
import com.limoz.fleet.maintenance.dto.ServiceTypeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ServiceTypeService {

    private final ServiceTypeRepository repository;
    private final MaintenanceTaskRepository taskRepository;
    private final MaintenanceScheduleRepository scheduleRepository;
    private final MaintenanceMapper mapper;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.REFERENCE_DATA, key = "'serviceTypes'")
    public List<ServiceTypeResponse> list() {
        return repository.findAllByOrderBySortOrderAscNameAsc().stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ServiceTypeResponse get(Long id) {
        return mapper.toResponse(load(id));
    }

    @Transactional(readOnly = true)
    public ServiceType load(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Service type", id));
    }

    @Transactional(readOnly = true)
    public ServiceType loadActive(Long id) {
        ServiceType type = load(id);
        if (!type.isActive()) {
            throw new BusinessRuleException("SERVICE_TYPE_INACTIVE", "Service type " + type.getName() + " is inactive");
        }
        return type;
    }

    @CacheEvict(cacheNames = CacheConfig.REFERENCE_DATA, allEntries = true)
    public ServiceTypeResponse create(ServiceTypeRequest request) {
        String code = request.code().trim().toUpperCase();
        if (repository.codeExists(code, null)) {
            throw new DuplicateResourceException("Service type code " + code + " already exists");
        }
        ServiceType type = new ServiceType();
        type.setCode(code);
        apply(type, request);
        ServiceTypeResponse response = mapper.toResponse(repository.save(type));
        auditService.record(AuditAction.CREATE, "ServiceType", type.getId(), code, null, response, "Service type created");
        return response;
    }

    @CacheEvict(cacheNames = CacheConfig.REFERENCE_DATA, allEntries = true)
    public ServiceTypeResponse update(Long id, ServiceTypeRequest request) {
        ServiceType type = load(id);
        String code = request.code().trim().toUpperCase();
        if (repository.codeExists(code, id)) {
            throw new DuplicateResourceException("Service type code " + code + " already exists");
        }
        ServiceTypeResponse before = mapper.toResponse(type);
        type.setCode(code);
        apply(type, request);
        ServiceTypeResponse after = mapper.toResponse(repository.save(type));
        auditService.record(AuditAction.UPDATE, "ServiceType", id, code, before, after, "Service type updated");
        return after;
    }

    /** Service types used by tasks or schedules are deactivated instead of deleted. */
    @CacheEvict(cacheNames = CacheConfig.REFERENCE_DATA, allEntries = true)
    public void delete(Long id) {
        ServiceType type = load(id);
        ServiceTypeResponse before = mapper.toResponse(type);
        if (taskRepository.countByServiceTypeId(id) > 0 || scheduleRepository.countByServiceTypeId(id) > 0) {
            type.setActive(false);
            repository.save(type);
            auditService.record(AuditAction.DISABLE, "ServiceType", id, type.getCode(), before, mapper.toResponse(type),
                    "Service type deactivated (in use by tasks or schedules)");
            return;
        }
        repository.delete(type);
        auditService.record(AuditAction.DELETE, "ServiceType", id, type.getCode(), before, null, "Service type deleted");
    }

    private void apply(ServiceType t, ServiceTypeRequest r) {
        t.setName(r.name().trim());
        t.setCategory(r.category() == null ? ServiceCategory.SERVICE : r.category());
        t.setDefaultIntervalKm(r.defaultIntervalKm());
        t.setDefaultIntervalDays(r.defaultIntervalDays());
        t.setActive(r.active() == null || r.active());
        t.setSortOrder(r.sortOrder() == null ? 0 : r.sortOrder());
    }
}
