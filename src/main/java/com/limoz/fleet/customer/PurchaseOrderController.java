package com.limoz.fleet.customer;

import com.limoz.fleet.common.api.PageResponse;
import com.limoz.fleet.customer.dto.PurchaseOrderFilter;
import com.limoz.fleet.customer.dto.PurchaseOrderReceiveRequest;
import com.limoz.fleet.customer.dto.PurchaseOrderRequest;
import com.limoz.fleet.customer.dto.PurchaseOrderResponse;
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

@RestController
@RequestMapping("/api/v1/purchase-orders")
@RequiredArgsConstructor
@Tag(name = "Purchase Orders", description = "Local purchase orders (LPOs) raised by clients that authorise work and back invoices")
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    @GetMapping
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @Operation(summary = "Search LPOs (paginated)", description = "Example: `?q=LPO-2026&customerId=3&status=OPEN&from=2026-01-01&to=2026-12-31`")
    public PageResponse<PurchaseOrderResponse> search(@ParameterObject PurchaseOrderFilter filter,
                                                      @ParameterObject @PageableDefault(size = 20, sort = "issuedDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return purchaseOrderService.search(filter, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    public PurchaseOrderResponse get(@PathVariable Long id) {
        return purchaseOrderService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CUSTOMER_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Raise an LPO")
    public PurchaseOrderResponse create(@Valid @RequestBody PurchaseOrderRequest request) {
        return purchaseOrderService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_MANAGE')")
    public PurchaseOrderResponse update(@PathVariable Long id, @Valid @RequestBody PurchaseOrderRequest request) {
        return purchaseOrderService.update(id, request);
    }

    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAuthority('CUSTOMER_MANAGE')")
    @Operation(summary = "Record receipt of the signed LPO document (optionally attaching the scan)")
    public PurchaseOrderResponse receive(@PathVariable Long id, @RequestBody(required = false) @Valid PurchaseOrderReceiveRequest request) {
        return purchaseOrderService.markReceived(id, request == null ? null : request.receivedDate(), request == null ? null : request.attachmentId());
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('CUSTOMER_MANAGE')")
    public PurchaseOrderResponse cancel(@PathVariable Long id, @RequestBody(required = false) @Valid ReasonRequest request) {
        return purchaseOrderService.cancel(id, request == null ? null : request.reason());
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('CUSTOMER_MANAGE')")
    public PurchaseOrderResponse close(@PathVariable Long id, @RequestBody(required = false) @Valid ReasonRequest request) {
        return purchaseOrderService.close(id, request == null ? null : request.reason());
    }

    @PostMapping("/refresh-statuses")
    @PreAuthorize("hasAuthority('CUSTOMER_MANAGE')")
    @Operation(summary = "Expire open LPOs whose expiry date has passed (also runs nightly)")
    public int refreshStatuses() {
        return purchaseOrderService.refreshStatuses();
    }
}
