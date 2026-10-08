package com.limoz.fleet.notification.alert.controller;

import com.limoz.fleet.notification.alert.domain.Alert;
import com.limoz.fleet.notification.alert.service.AlertService;

import com.limoz.fleet.common.api.PageResponse;
import com.limoz.fleet.notification.alert.dto.AlertFilter;
import com.limoz.fleet.notification.alert.dto.AlertNoteRequest;
import com.limoz.fleet.notification.alert.dto.AlertResponse;
import com.limoz.fleet.notification.alert.dto.AlertScanResult;
import com.limoz.fleet.notification.alert.dto.AlertSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
@Tag(name = "Alert centre", description = "Management alerts detected by periodic scans (GPS, documents, licences, movement, vehicles)")
public class AlertController {

    private final AlertService alertService;

    @GetMapping
    @PreAuthorize("hasAuthority('DASHBOARD_VIEW')")
    @Operation(summary = "Alerts ordered ACTIVE first, CRITICAL first, then most recently detected",
            description = "Filters: status, severity, type, entityType. Sorting is fixed; page/size apply.")
    public PageResponse<AlertResponse> search(@ParameterObject AlertFilter filter,
                                              @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return alertService.search(filter, pageable);
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('DASHBOARD_VIEW')")
    @Operation(summary = "Open alerts by severity, all alerts by status and the top open types (dashboard widget)")
    public AlertSummaryResponse summary() {
        return alertService.summary();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('DASHBOARD_VIEW')")
    public AlertResponse get(@PathVariable Long id) {
        return alertService.get(id);
    }

    @PostMapping("/{id}/acknowledge")
    @PreAuthorize("hasAuthority('ALERT_MANAGE')")
    @Operation(summary = "Acknowledge an ACTIVE alert (somebody is looking at it)")
    public AlertResponse acknowledge(@PathVariable Long id, @Valid @RequestBody(required = false) AlertNoteRequest request) {
        return alertService.acknowledge(id, request);
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAuthority('ALERT_MANAGE')")
    @Operation(summary = "Resolve an alert manually (it is re-raised by the next scan if the condition persists)")
    public AlertResponse resolve(@PathVariable Long id, @Valid @RequestBody(required = false) AlertNoteRequest request) {
        return alertService.resolve(id, request);
    }

    @PostMapping("/scan")
    @PreAuthorize("hasAuthority('ALERT_MANAGE')")
    @Operation(summary = "Run all alert scanners now (also runs on the alert-scan schedule)")
    public AlertScanResult scan() {
        return alertService.runScan();
    }
}
