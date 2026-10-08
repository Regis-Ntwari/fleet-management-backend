package com.limoz.fleet.finance.controller;

import com.limoz.fleet.finance.domain.Expense;
import com.limoz.fleet.finance.service.ExpenseService;

import com.limoz.fleet.common.api.PageResponse;
import com.limoz.fleet.finance.dto.ExpenseFilter;
import com.limoz.fleet.finance.dto.ExpenseRejectRequest;
import com.limoz.fleet.finance.dto.ExpenseRequest;
import com.limoz.fleet.finance.dto.ExpenseResponse;
import com.limoz.fleet.finance.dto.ExpenseSummaryResponse;
import com.limoz.fleet.finance.dto.SettlementRequest;
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
@RequestMapping("/api/v1/expenses")
@RequiredArgsConstructor
@Tag(name = "Expenses", description = "Operational costs outside fuel and maintenance, with approval and payment")
public class ExpenseController {

    private final ExpenseService expenseService;

    @GetMapping
    @PreAuthorize("hasAuthority('FINANCE_READ')")
    @Operation(summary = "Search expenses (paginated)", description = "Example: `?q=permit&status=PENDING&categoryId=5&from=2026-06-01`")
    public PageResponse<ExpenseResponse> search(@ParameterObject ExpenseFilter filter,
                                                @ParameterObject @PageableDefault(size = 20, sort = "incurredOn", direction = Sort.Direction.DESC) Pageable pageable) {
        return expenseService.search(filter, pageable);
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('FINANCE_READ')")
    @Operation(summary = "Expense totals by status, category and vehicle for a period (default last 30 days)")
    public ExpenseSummaryResponse summary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return expenseService.summary(from, to);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('FINANCE_READ')")
    public ExpenseResponse get(@PathVariable Long id) {
        return expenseService.get(id);
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit an expense claim (any staff member; approval and payment are gated)")
    public ExpenseResponse submit(@Valid @RequestBody ExpenseRequest request) {
        return expenseService.submit(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Edit a pending expense (submitter or FINANCE_MANAGE)")
    public ExpenseResponse update(@PathVariable Long id, @Valid @RequestBody ExpenseRequest request) {
        return expenseService.update(id, request);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('EXPENSE_APPROVE')")
    public ExpenseResponse approve(@PathVariable Long id) {
        return expenseService.approve(id);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('EXPENSE_APPROVE')")
    public ExpenseResponse reject(@PathVariable Long id, @Valid @RequestBody ExpenseRejectRequest request) {
        return expenseService.reject(id, request.reason());
    }

    @PostMapping("/{id}/pay")
    @PreAuthorize("hasAuthority('FINANCE_MANAGE')")
    @Operation(summary = "Pay an approved expense (records an OUT payment in the ledger)")
    public ExpenseResponse pay(@PathVariable Long id, @Valid @RequestBody SettlementRequest request) {
        return expenseService.markPaid(id, request);
    }
}
