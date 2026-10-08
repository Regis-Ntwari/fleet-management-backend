package com.limoz.fleet.reporting.controller;

import com.limoz.fleet.reporting.domain.ReportParams;
import com.limoz.fleet.reporting.service.ReportService;

import com.limoz.fleet.reporting.dto.ReportDescriptor;
import com.limoz.fleet.reporting.export.domain.ExportedReport;
import com.limoz.fleet.reporting.export.domain.ReportFormat;
import com.limoz.fleet.reporting.export.domain.ReportTable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "Operational reports generated on the server; export as CSV, Excel or PDF")
public class ReportController {

    private final ReportService reportService;

    @GetMapping
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    @Operation(summary = "List the reports available to the current user")
    public List<ReportDescriptor> available() {
        return reportService.available();
    }

    @GetMapping("/{code}")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    @Operation(summary = "Run a report",
            description = "`format=json` (default) returns the table structure for on-screen rendering; `csv`, `xlsx` and `pdf` " +
                    "download a file (requires REPORT_EXPORT). Filters: from, to, date, vehicleId, driverId, categoryId, customerId, status, department.")
    public ResponseEntity<?> run(@PathVariable String code,
                                 @RequestParam(defaultValue = "json") String format,
                                 @ParameterObject ReportParams params) {
        ReportFormat reportFormat = ReportFormat.valueOf(format.trim().toUpperCase());
        if (reportFormat == ReportFormat.JSON) {
            ReportTable table = reportService.build(code, params);
            return ResponseEntity.ok(table);
        }
        if (!com.limoz.fleet.security.SecurityUtils.hasPermission("REPORT_EXPORT")) {
            throw new org.springframework.security.access.AccessDeniedException("REPORT_EXPORT required");
        }
        ExportedReport exported = reportService.export(code, params, reportFormat);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + exported.fileName() + "\"")
                .contentType(MediaType.parseMediaType(exported.contentType()))
                .body(exported.content());
    }
}
