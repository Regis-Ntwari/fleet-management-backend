package com.limoz.fleet.customer;

import com.limoz.fleet.common.api.PageResponse;
import com.limoz.fleet.customer.dto.CustomerRequest;
import com.limoz.fleet.customer.dto.CustomerResponse;
import com.limoz.fleet.customer.dto.CustomerSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
@Tag(name = "Clients", description = "Corporate accounts LIMOZ provides transport for")
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @Operation(summary = "Search clients (paginated)")
    public PageResponse<CustomerResponse> search(@RequestParam(required = false) String q,
                                                 @RequestParam(required = false) CustomerType type,
                                                 @RequestParam(required = false) Boolean active,
                                                 @ParameterObject @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return customerService.search(q, type, active, pageable);
    }

    @GetMapping("/summaries")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    public List<CustomerSummary> summaries() {
        return customerService.summaries();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    public CustomerResponse get(@PathVariable Long id) {
        return customerService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CUSTOMER_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse create(@Valid @RequestBody CustomerRequest request) {
        return customerService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_MANAGE')")
    public CustomerResponse update(@PathVariable Long id, @Valid @RequestBody CustomerRequest request) {
        return customerService.update(id, request);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('CUSTOMER_MANAGE')")
    public CustomerResponse activate(@PathVariable Long id) {
        return customerService.setActive(id, true);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('CUSTOMER_MANAGE')")
    public CustomerResponse deactivate(@PathVariable Long id) {
        return customerService.setActive(id, false);
    }
}
