package com.limoz.fleet.customer;

import com.limoz.fleet.common.api.PageResponse;
import com.limoz.fleet.customer.dto.CommitmentFilter;
import com.limoz.fleet.customer.dto.CommitmentRequest;
import com.limoz.fleet.customer.dto.CommitmentResponse;
import com.limoz.fleet.customer.dto.CommitmentSummary;
import com.limoz.fleet.customer.dto.ReasonRequest;
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
@RequestMapping("/api/v1/commitments")
@RequiredArgsConstructor
@Tag(name = "Commitments", description = "Framework contracts with corporate clients that bookings and LPOs draw down against")
public class CommitmentController {

    private final CommitmentService commitmentService;

    @GetMapping
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @Operation(summary = "Search commitments (paginated)", description = "Example: `?q=shuttle&customerId=3&status=ACTIVE&status=EXPIRING_SOON`")
    public PageResponse<CommitmentResponse> search(@ParameterObject CommitmentFilter filter,
                                                   @ParameterObject @PageableDefault(size = 20, sort = "periodEnd", direction = Sort.Direction.DESC) Pageable pageable) {
        return commitmentService.search(filter, pageable);
    }

    @GetMapping("/summaries")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @Operation(summary = "Open commitments for dropdowns (optionally for one client)")
    public List<CommitmentSummary> summaries(@RequestParam(required = false) Long customerId) {
        return commitmentService.summaries(customerId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    public CommitmentResponse get(@PathVariable Long id) {
        return commitmentService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CUSTOMER_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a commitment (starts as DRAFT)")
    public CommitmentResponse create(@Valid @RequestBody CommitmentRequest request) {
        return commitmentService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_MANAGE')")
    public CommitmentResponse update(@PathVariable Long id, @Valid @RequestBody CommitmentRequest request) {
        return commitmentService.update(id, request);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('CUSTOMER_MANAGE')")
    @Operation(summary = "Activate a signed commitment (DRAFT -> ACTIVE)")
    public CommitmentResponse activate(@PathVariable Long id) {
        return commitmentService.activate(id);
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('CUSTOMER_MANAGE')")
    @Operation(summary = "Close a commitment (fully consumed or ended)")
    public CommitmentResponse close(@PathVariable Long id, @RequestBody(required = false) @Valid ReasonRequest request) {
        return commitmentService.close(id, request == null ? null : request.reason());
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('CUSTOMER_MANAGE')")
    public CommitmentResponse cancel(@PathVariable Long id, @RequestBody(required = false) @Valid ReasonRequest request) {
        return commitmentService.cancel(id, request == null ? null : request.reason());
    }

    @PostMapping("/refresh-statuses")
    @PreAuthorize("hasAuthority('CUSTOMER_MANAGE')")
    @Operation(summary = "Recompute EXPIRING_SOON / CLOSED on active commitments (also runs nightly)")
    public int refreshStatuses() {
        return commitmentService.refreshStatuses();
    }
}
