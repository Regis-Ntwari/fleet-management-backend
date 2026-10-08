package com.limoz.fleet.maintenance.inventory.controller;

import com.limoz.fleet.maintenance.inventory.service.StockMovementService;

import com.limoz.fleet.common.api.PageResponse;
import com.limoz.fleet.maintenance.inventory.dto.StockMovementFilter;
import com.limoz.fleet.maintenance.inventory.dto.StockMovementRequest;
import com.limoz.fleet.maintenance.inventory.dto.StockMovementResponse;
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
@RequestMapping("/api/v1/stock-movements")
@RequiredArgsConstructor
@Tag(name = "Stock Movements", description = "Store ledger: receipts (PO-xxxx), issues to maintenance jobs (MNT-...), returns and adjustments")
public class StockMovementController {

    private final StockMovementService stockMovementService;

    @GetMapping
    @PreAuthorize("hasAuthority('INVENTORY_READ')")
    @Operation(summary = "Search the stock ledger", description = "Example: `?movementType=OUT&from=2026-06-01&to=2026-06-30&reference=MNT-2026`")
    public PageResponse<StockMovementResponse> search(@ParameterObject StockMovementFilter filter,
                                                      @ParameterObject @PageableDefault(size = 20, sort = "movedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return stockMovementService.search(filter, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_READ')")
    public StockMovementResponse get(@PathVariable Long id) {
        return stockMovementService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('INVENTORY_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a stock movement (IN / OUT / RETURN / ADJUSTMENT); stock cannot go below zero")
    public StockMovementResponse record(@Valid @RequestBody StockMovementRequest request) {
        return stockMovementService.record(request);
    }
}
