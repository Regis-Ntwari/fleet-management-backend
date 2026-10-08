package com.limoz.fleet.vehicle.service;

import com.limoz.fleet.vehicle.domain.Vehicle;
import com.limoz.fleet.vehicle.domain.VehicleCategory;
import com.limoz.fleet.vehicle.mapper.VehicleMapper;
import com.limoz.fleet.vehicle.repository.VehicleCategoryRepository;
import com.limoz.fleet.vehicle.repository.VehicleRepository;

import com.limoz.fleet.audit.domain.AuditAction;
import com.limoz.fleet.audit.service.AuditService;
import com.limoz.fleet.common.exception.BusinessRuleException;
import com.limoz.fleet.common.exception.DuplicateResourceException;
import com.limoz.fleet.common.exception.ResourceNotFoundException;
import com.limoz.fleet.config.CacheConfig;
import com.limoz.fleet.vehicle.dto.VehicleCategoryRequest;
import com.limoz.fleet.vehicle.dto.VehicleCategoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class VehicleCategoryService {

    private final VehicleCategoryRepository repository;
    private final VehicleRepository vehicleRepository;
    private final VehicleMapper mapper;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.REFERENCE_DATA, key = "'vehicleCategories'")
    public List<VehicleCategoryResponse> list() {
        return repository.findAllByOrderBySortOrderAscNameAsc().stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public VehicleCategory load(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Vehicle category", id));
    }

    @CacheEvict(cacheNames = CacheConfig.REFERENCE_DATA, allEntries = true)
    public VehicleCategoryResponse create(VehicleCategoryRequest request) {
        if (repository.existsByCodeIgnoreCase(request.code())) {
            throw new DuplicateResourceException("Category code " + request.code() + " already exists");
        }
        if (repository.existsByNameIgnoreCase(request.name())) {
            throw new DuplicateResourceException("Category name " + request.name() + " already exists");
        }
        validateSeats(request);
        VehicleCategory category = new VehicleCategory();
        apply(category, request);
        VehicleCategoryResponse response = mapper.toResponse(repository.save(category));
        auditService.record(AuditAction.CREATE, "VehicleCategory", category.getId(), category.getCode(), null, response, "Vehicle category created");
        return response;
    }

    @CacheEvict(cacheNames = CacheConfig.REFERENCE_DATA, allEntries = true)
    public VehicleCategoryResponse update(Long id, VehicleCategoryRequest request) {
        VehicleCategory category = load(id);
        VehicleCategoryResponse before = mapper.toResponse(category);
        if (!category.getCode().equalsIgnoreCase(request.code()) && repository.existsByCodeIgnoreCase(request.code())) {
            throw new DuplicateResourceException("Category code " + request.code() + " already exists");
        }
        if (!category.getName().equalsIgnoreCase(request.name()) && repository.existsByNameIgnoreCase(request.name())) {
            throw new DuplicateResourceException("Category name " + request.name() + " already exists");
        }
        validateSeats(request);
        apply(category, request);
        VehicleCategoryResponse after = mapper.toResponse(repository.save(category));
        auditService.record(AuditAction.UPDATE, "VehicleCategory", id, category.getCode(), before, after, "Vehicle category updated");
        return after;
    }

    @CacheEvict(cacheNames = CacheConfig.REFERENCE_DATA, allEntries = true)
    public void delete(Long id) {
        VehicleCategory category = load(id);
        long inUse = vehicleRepository.count((root, q, cb) -> cb.equal(root.get("category").get("id"), id));
        if (inUse > 0) {
            throw new BusinessRuleException("CATEGORY_IN_USE", "Category is used by " + inUse + " vehicle(s); deactivate it instead");
        }
        repository.delete(category);
        auditService.record(AuditAction.DELETE, "VehicleCategory", id, category.getCode(), mapper.toResponse(category), null, "Vehicle category deleted");
    }

    private void validateSeats(VehicleCategoryRequest request) {
        if (request.minSeats() != null && request.maxSeats() != null && request.minSeats() > request.maxSeats()) {
            throw new BusinessRuleException("Minimum seats cannot exceed maximum seats");
        }
    }

    private void apply(VehicleCategory c, VehicleCategoryRequest r) {
        c.setCode(r.code());
        c.setName(r.name().trim());
        c.setDescription(r.description());
        c.setMinSeats(r.minSeats());
        c.setMaxSeats(r.maxSeats());
        c.setDefaultDayRate(r.defaultDayRate());
        c.setActive(r.active() == null || r.active());
        c.setSortOrder(r.sortOrder() == null ? 0 : r.sortOrder());
    }
}
