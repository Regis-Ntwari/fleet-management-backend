package com.limoz.fleet.finance;

import com.limoz.fleet.finance.dto.FinanceSummaryResponse;
import com.limoz.fleet.finance.dto.VehicleCostResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Finance Reports", description = "Consolidated finance KPIs and vehicle cost of ownership")
public class FinanceController {

    private final FinanceSummaryService financeSummaryService;
    private final VehicleCostService vehicleCostService;

    @GetMapping("/finance/summary")
    @PreAuthorize("hasAuthority('FINANCE_READ')")
    @Operation(summary = "Invoiced, paid, outstanding, overdue, payments in/out and expenses by category (RWF; default last 30 days)")
    public FinanceSummaryResponse summary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return financeSummaryService.summary(from, to);
    }

    @GetMapping("/vehicles/{id}/costs")
    @PreAuthorize("hasAuthority('FINANCE_READ')")
    @Operation(summary = "Vehicle costs: fuel, maintenance, expenses, fines, total and cost per km (all time when no range is given)")
    public VehicleCostResponse vehicleCosts(@PathVariable Long id,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return vehicleCostService.costs(id, from, to);
    }
}
