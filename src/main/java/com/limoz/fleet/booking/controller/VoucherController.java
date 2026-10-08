package com.limoz.fleet.booking.controller;

import com.limoz.fleet.booking.service.VoucherService;

import com.limoz.fleet.booking.dto.ReturnRequest;
import com.limoz.fleet.booking.dto.VoucherFilter;
import com.limoz.fleet.booking.dto.VoucherResponse;
import com.limoz.fleet.booking.dto.VoucherUpdateRequest;
import com.limoz.fleet.common.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/vouchers")
@RequiredArgsConstructor
@Tag(name = "Deployment Vouchers", description = "Per-vehicle deployment vouchers (LIMOZ/000628/2026)")
public class VoucherController {

    private final VoucherService voucherService;

    @GetMapping
    @PreAuthorize("hasAuthority('BOOKING_READ')")
    @Operation(summary = "Search vouchers (paginated)",
            description = "Example: `?q=LIMOZ/0006&status=ONGOING&vehicleId=3&from=2026-06-01&to=2026-06-30`")
    public PageResponse<VoucherResponse> search(@ParameterObject VoucherFilter filter,
                                                @ParameterObject @PageableDefault(size = 20, sort = "voucherDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return voucherService.search(filter, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('BOOKING_READ')")
    public VoucherResponse get(@PathVariable Long id) {
        return voucherService.get(id);
    }

    @PutMapping("/{id}/return")
    @PreAuthorize("hasAuthority('DISPATCH_MANAGE')")
    @Operation(summary = "Record the return on a voucher (same as returning its slot)")
    public VoucherResponse recordReturn(@PathVariable Long id, @Valid @RequestBody ReturnRequest request) {
        return voucherService.recordReturn(id, request);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('DISPATCH_MANAGE','FINANCE_MANAGE')")
    @Operation(summary = "Edit purchase order, amounts, comment and observation of a voucher")
    public VoucherResponse update(@PathVariable Long id, @Valid @RequestBody VoucherUpdateRequest request) {
        return voucherService.update(id, request);
    }
}
