package com.limoz.fleet.finance.service;

import com.limoz.fleet.finance.domain.Expense;
import com.limoz.fleet.finance.domain.ExpenseCategory;
import com.limoz.fleet.finance.mapper.FinanceMapper;
import com.limoz.fleet.finance.repository.ExpenseCategoryRepository;
import com.limoz.fleet.finance.repository.ExpenseRepository;

import com.limoz.fleet.audit.domain.AuditAction;
import com.limoz.fleet.audit.service.AuditService;
import com.limoz.fleet.common.exception.BusinessRuleException;
import com.limoz.fleet.common.exception.DuplicateResourceException;
import com.limoz.fleet.common.exception.ResourceNotFoundException;
import com.limoz.fleet.config.CacheConfig;
import com.limoz.fleet.finance.dto.ExpenseCategoryRequest;
import com.limoz.fleet.finance.dto.ExpenseCategoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ExpenseCategoryService {

    private final ExpenseCategoryRepository repository;
    private final ExpenseRepository expenseRepository;
    private final FinanceMapper mapper;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.REFERENCE_DATA, key = "'expenseCategories'")
    public List<ExpenseCategoryResponse> list() {
        return repository.findAllByOrderBySortOrderAscNameAsc().stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ExpenseCategory load(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Expense category", id));
    }

    @Transactional(readOnly = true)
    public ExpenseCategory loadActive(Long id) {
        ExpenseCategory category = load(id);
        if (!category.isActive()) {
            throw new BusinessRuleException("CATEGORY_INACTIVE", "Expense category " + category.getName() + " is inactive");
        }
        return category;
    }

    @CacheEvict(cacheNames = CacheConfig.REFERENCE_DATA, allEntries = true)
    public ExpenseCategoryResponse create(ExpenseCategoryRequest request) {
        if (repository.existsByCodeIgnoreCase(request.code())) {
            throw new DuplicateResourceException("Expense category code " + request.code() + " already exists");
        }
        ExpenseCategory category = new ExpenseCategory();
        category.setCode(request.code());
        apply(category, request);
        ExpenseCategoryResponse response = mapper.toResponse(repository.save(category));
        auditService.record(AuditAction.CREATE, "ExpenseCategory", category.getId(), category.getCode(), null, response, "Expense category created");
        return response;
    }

    @CacheEvict(cacheNames = CacheConfig.REFERENCE_DATA, allEntries = true)
    public ExpenseCategoryResponse update(Long id, ExpenseCategoryRequest request) {
        ExpenseCategory category = load(id);
        if (!category.getCode().equalsIgnoreCase(request.code()) && repository.existsByCodeIgnoreCase(request.code())) {
            throw new DuplicateResourceException("Expense category code " + request.code() + " already exists");
        }
        ExpenseCategoryResponse before = mapper.toResponse(category);
        category.setCode(request.code());
        apply(category, request);
        ExpenseCategoryResponse after = mapper.toResponse(repository.save(category));
        auditService.record(AuditAction.UPDATE, "ExpenseCategory", id, category.getCode(), before, after, "Expense category updated");
        return after;
    }

    @CacheEvict(cacheNames = CacheConfig.REFERENCE_DATA, allEntries = true)
    public void delete(Long id) {
        ExpenseCategory category = load(id);
        long inUse = expenseRepository.countByCategoryId(id);
        if (inUse > 0) {
            throw new BusinessRuleException("CATEGORY_IN_USE", "Category is used by " + inUse + " expense(s); deactivate it instead");
        }
        repository.delete(category);
        auditService.record(AuditAction.DELETE, "ExpenseCategory", id, category.getCode(), mapper.toResponse(category), null, "Expense category deleted");
    }

    private void apply(ExpenseCategory c, ExpenseCategoryRequest r) {
        c.setName(r.name().trim());
        c.setActive(r.active() == null || r.active());
        c.setSortOrder(r.sortOrder() == null ? 0 : r.sortOrder());
    }
}
