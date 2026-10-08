package com.limoz.fleet.customer.service;

import com.limoz.fleet.customer.domain.Customer;
import com.limoz.fleet.customer.domain.CustomerType;
import com.limoz.fleet.customer.mapper.CustomerMapper;
import com.limoz.fleet.customer.repository.CustomerRepository;

import com.limoz.fleet.audit.domain.AuditAction;
import com.limoz.fleet.audit.service.AuditService;
import com.limoz.fleet.common.api.PageResponse;
import com.limoz.fleet.common.exception.BusinessRuleException;
import com.limoz.fleet.common.exception.DuplicateResourceException;
import com.limoz.fleet.common.exception.ResourceNotFoundException;
import com.limoz.fleet.common.util.Specifications;
import com.limoz.fleet.customer.dto.CustomerRequest;
import com.limoz.fleet.customer.dto.CustomerResponse;
import com.limoz.fleet.customer.dto.CustomerSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CustomerService {

    private final CustomerRepository repository;
    private final CustomerMapper mapper;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> search(String q, CustomerType type, Boolean active, Pageable pageable) {
        return PageResponse.from(repository.findAll(Specifications.and(
                Specifications.likeAny(q, "name", "tin", "customerCode", "contactPerson", "email"),
                Specifications.equal("customerType", type),
                Specifications.equal("active", active)), pageable).map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    public List<CustomerSummary> summaries() {
        return repository.findByActiveTrueOrderByNameAsc().stream().map(mapper::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(Long id) {
        return mapper.toResponse(load(id));
    }

    @Transactional(readOnly = true)
    public Customer load(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Customer", id));
    }

    @Transactional(readOnly = true)
    public Customer loadActive(Long id) {
        Customer customer = load(id);
        if (!customer.isActive()) {
            throw new BusinessRuleException("CUSTOMER_INACTIVE", "Customer " + customer.getName() + " is inactive");
        }
        return customer;
    }

    public CustomerResponse create(CustomerRequest request) {
        validate(request, null);
        Customer customer = new Customer();
        apply(customer, request);
        CustomerResponse response = mapper.toResponse(repository.save(customer));
        auditService.record(AuditAction.CREATE, "Customer", customer.getId(), customer.getName(), null, response, "Client created");
        return response;
    }

    public CustomerResponse update(Long id, CustomerRequest request) {
        Customer customer = load(id);
        validate(request, id);
        CustomerResponse before = mapper.toResponse(customer);
        apply(customer, request);
        CustomerResponse after = mapper.toResponse(repository.save(customer));
        auditService.record(AuditAction.UPDATE, "Customer", id, customer.getName(), before, after, "Client updated");
        return after;
    }

    public CustomerResponse setActive(Long id, boolean active) {
        Customer customer = load(id);
        CustomerResponse before = mapper.toResponse(customer);
        customer.setActive(active);
        CustomerResponse after = mapper.toResponse(repository.save(customer));
        auditService.record(active ? AuditAction.ENABLE : AuditAction.DISABLE, "Customer", id, customer.getName(), before, after,
                active ? "Client activated" : "Client deactivated");
        return after;
    }

    private void validate(CustomerRequest r, Long excludeId) {
        if (repository.nameExists(r.name().trim(), excludeId)) {
            throw new DuplicateResourceException("A client named " + r.name() + " already exists");
        }
        if (r.tin() != null && !r.tin().isBlank() && repository.tinExists(r.tin().trim(), excludeId)) {
            throw new DuplicateResourceException("A client with TIN " + r.tin() + " already exists");
        }
    }

    private void apply(Customer c, CustomerRequest r) {
        c.setCustomerCode(r.customerCode() == null || r.customerCode().isBlank() ? null : r.customerCode().trim().toUpperCase());
        c.setName(r.name().trim());
        c.setCustomerType(r.customerType() == null ? CustomerType.CORPORATE : r.customerType());
        c.setTin(r.tin() == null || r.tin().isBlank() ? null : r.tin().trim());
        c.setContactPerson(r.contactPerson());
        c.setEmail(r.email() == null ? null : r.email().trim().toLowerCase());
        c.setPhone(r.phone());
        c.setAddress(r.address());
        c.setCity(r.city());
        c.setCountry(r.country() == null ? "Rwanda" : r.country());
        c.setAccountManagerUserId(r.accountManagerUserId());
        c.setCreditLimit(r.creditLimit());
        c.setActive(r.active() == null || r.active());
        c.setNotes(r.notes());
    }
}
