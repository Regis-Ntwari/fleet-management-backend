package com.limoz.fleet.finance.dto;

import com.limoz.fleet.finance.domain.ExpenseStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Expense KPIs for a period: totals by status, category and vehicle (RWF, USD converted). */
public record ExpenseSummaryResponse(
        LocalDate from,
        LocalDate to,
        String currency,
        long total,
        BigDecimal totalAmount,
        Map<ExpenseStatus, Long> countByStatus,
        Map<ExpenseStatus, BigDecimal> amountByStatus,
        List<FinanceSummaryResponse.CategoryAmount> byCategory,
        List<VehicleAmount> byVehicle) {

    public record VehicleAmount(Long vehicleId, String plateNumber, long count, BigDecimal amount) {}
}
