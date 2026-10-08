package com.limoz.fleet.finance.controller;

import com.limoz.fleet.finance.service.PaymentService;

import com.limoz.fleet.common.api.PageResponse;
import com.limoz.fleet.finance.dto.PaymentFilter;
import com.limoz.fleet.finance.dto.PaymentRequest;
import com.limoz.fleet.finance.dto.PaymentResponse;
import com.limoz.fleet.finance.dto.PaymentReversalRequest;
import com.limoz.fleet.finance.dto.PaymentSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Ledger of money received from clients and paid out to vendors, garages, authorities and staff")
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping
    @PreAuthorize("hasAuthority('FINANCE_READ')")
    @Operation(summary = "Search payments (paginated)", description = "Example: `?direction=IN&method=MOBILE_MONEY&from=2026-06-01&q=PAY-33`")
    public PageResponse<PaymentResponse> search(@ParameterObject PaymentFilter filter,
                                                @ParameterObject @PageableDefault(size = 20, sort = "paidAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return paymentService.search(filter, pageable);
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('FINANCE_READ')")
    @Operation(summary = "Received / paid out for a period (default last 30 days), consolidated in RWF")
    public PaymentSummaryResponse summary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return paymentService.summary(from, to);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('FINANCE_READ')")
    public PaymentResponse get(@PathVariable Long id) {
        return paymentService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('FINANCE_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a payment, optionally settling an invoice, maintenance job, expense or traffic fine")
    public PaymentResponse record(@Valid @RequestBody PaymentRequest request) {
        return paymentService.record(request);
    }

    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasAuthority('FINANCE_MANAGE')")
    @Operation(summary = "Reverse a payment (kept in the ledger; the settled record is recomputed)")
    public PaymentResponse reverse(@PathVariable Long id, @Valid @RequestBody PaymentReversalRequest request) {
        return paymentService.reverse(id, request.reason());
    }
}
