package com.limoz.fleet.maintenance.inventory.controller;

import com.limoz.fleet.maintenance.inventory.service.SparePartService;
import com.limoz.fleet.maintenance.inventory.service.StockMovementService;

import com.limoz.fleet.common.api.PageResponse;
import com.limoz.fleet.maintenance.inventory.dto.InventorySummaryResponse;
import com.limoz.fleet.maintenance.inventory.dto.SparePartFilter;
import com.limoz.fleet.maintenance.inventory.dto.SparePartRequest;
import com.limoz.fleet.maintenance.inventory.dto.SparePartResponse;
import com.limoz.fleet.maintenance.inventory.dto.StockMovementFilter;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/spare-parts")
@RequiredArgsConstructor
@Tag(name = "Spare Parts", description = "Workshop store catalogue with derived OK / LOW / OUT stock status")
public class SparePartController {

    private final SparePartService sparePartService;
    private final StockMovementService stockMovementService;

    @GetMapping
    @PreAuthorize("hasAuthority('INVENTORY_READ')")
    @Operation(summary = "Search spare parts", description = "Example: `?q=brake&status=LOW&category=Brakes&page=0&size=20`")
    public PageResponse<SparePartResponse> search(@ParameterObject SparePartFilter filter,
                                                  @ParameterObject @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return sparePartService.search(filter, pageable);
    }

    @GetMapping("/low-stock")
    @PreAuthorize("hasAuthority('INVENTORY_READ')")
    @Operation(summary = "Active parts at or below their minimum stock (OUT first)")
    public List<SparePartResponse> lowStock() {
        return sparePartService.lowStock();
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('INVENTORY_READ')")
    @Operation(summary = "Part count, low / out of stock counts and total stock value")
    public InventorySummaryResponse summary() {
        return sparePartService.summary();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_READ')")
    public SparePartResponse get(@PathVariable Long id) {
        return sparePartService.get(id);
    }

    @GetMapping("/{id}/movements")
    @PreAuthorize("hasAuthority('INVENTORY_READ')")
    @Operation(summary = "Ledger of one part")
    public PageResponse<StockMovementResponse> movements(@PathVariable Long id,
                                                         @ParameterObject @PageableDefault(size = 20, sort = "movedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        sparePartService.get(id);
        return stockMovementService.search(new StockMovementFilter(null, id, null, null, null, null, null), pageable);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('INVENTORY_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Catalogue a spare part (opening stock becomes an IN movement)")
    public SparePartResponse create(@Valid @RequestBody SparePartRequest request) {
        return sparePartService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_MANAGE')")
    @Operation(summary = "Update catalogue data (stock level changes only through stock movements)")
    public SparePartResponse update(@PathVariable Long id, @Valid @RequestBody SparePartRequest request) {
        return sparePartService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_MANAGE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Deactivate a part (deleted outright when it has no ledger history)")
    public void deactivate(@PathVariable Long id) {
        sparePartService.deactivate(id);
    }
}
