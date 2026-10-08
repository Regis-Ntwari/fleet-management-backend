package com.limoz.fleet.incident.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Traffic fines KPIs: total, unpaid, disputed and the outstanding amount (unpaid + disputed). */
public record TrafficFineSummaryResponse(
        LocalDate from,
        LocalDate to,
        long total,
        long unpaidCount,
        BigDecimal unpaidAmount,
        long disputedCount,
        BigDecimal disputedAmount,
        long paidCount,
        BigDecimal paidAmount,
        long waivedCount,
        BigDecimal outstandingAmount) {}
