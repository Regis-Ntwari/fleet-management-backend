package com.limoz.fleet.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Consolidated finance figures in RWF (USD documents converted at the configured rate). */
public record FinanceSummaryResponse(
        LocalDate from,
        LocalDate to,
        String currency,
        BigDecimal usdToRwfRate,
        long invoicedCount,
        BigDecimal invoicedAmount,
        BigDecimal paidAmount,
        long outstandingCount,
        BigDecimal outstandingAmount,
        long overdueCount,
        BigDecimal overdueAmount,
        long readyToBillCount,
        long paymentCount,
        BigDecimal paymentsIn,
        BigDecimal paymentsOut,
        BigDecimal expensesTotal,
        List<CategoryAmount> expensesByCategory) {

    public record CategoryAmount(Long categoryId, String code, String name, long count, BigDecimal amount) {}
}
