package com.limoz.fleet.reporting;

import java.time.LocalDate;

/** Common filter parameters accepted by every report; each report uses the ones that apply. */
public record ReportParams(LocalDate from, LocalDate to, LocalDate date, Long vehicleId, Long driverId, Long categoryId,
                           Long customerId, String status, String department) {

    public LocalDate fromOr(LocalDate fallback) {
        return from == null ? fallback : from;
    }

    public LocalDate toOr(LocalDate fallback) {
        return to == null ? fallback : to;
    }

    public LocalDate dateOr(LocalDate fallback) {
        return date == null ? fallback : date;
    }
}
