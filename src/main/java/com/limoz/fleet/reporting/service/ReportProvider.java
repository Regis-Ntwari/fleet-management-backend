package com.limoz.fleet.reporting.service;

import com.limoz.fleet.reporting.controller.ReportController;
import com.limoz.fleet.reporting.domain.ReportParams;

import com.limoz.fleet.reporting.export.domain.ReportTable;

/**
 * A report = a named, permission-guarded generator of a {@link ReportTable}. Modules register providers as beans;
 * the ReportController discovers them, so adding a report never touches shared code.
 */
public interface ReportProvider {

    /** URL code, e.g. "daily-fleet". */
    String code();

    String title();

    String description();

    /** Permission needed in addition to REPORT_VIEW (e.g. FINANCE_READ for cost reports); null = none. */
    default String additionalPermission() {
        return null;
    }

    ReportTable build(ReportParams params);
}
