package com.limoz.fleet.reporting.service;

import com.limoz.fleet.reporting.domain.ReportParams;

import com.limoz.fleet.audit.domain.AuditAction;
import com.limoz.fleet.audit.service.AuditService;
import com.limoz.fleet.common.exception.ResourceNotFoundException;
import com.limoz.fleet.reporting.dto.ReportDescriptor;
import com.limoz.fleet.reporting.export.domain.ExportedReport;
import com.limoz.fleet.reporting.export.service.ReportExportService;
import com.limoz.fleet.reporting.export.domain.ReportFormat;
import com.limoz.fleet.reporting.export.domain.ReportTable;
import com.limoz.fleet.security.SecurityUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReportService {

    private final Map<String, ReportProvider> providers;
    private final ReportExportService exportService;
    private final AuditService auditService;

    public ReportService(List<ReportProvider> providers, ReportExportService exportService, AuditService auditService) {
        this.providers = providers.stream().collect(Collectors.toMap(ReportProvider::code, Function.identity()));
        this.exportService = exportService;
        this.auditService = auditService;
    }

    public List<ReportDescriptor> available() {
        List<String> formats = Arrays.stream(ReportFormat.values()).map(f -> f.name().toLowerCase()).toList();
        return providers.values().stream()
                .filter(p -> p.additionalPermission() == null || SecurityUtils.hasPermission(p.additionalPermission()))
                .sorted(Comparator.comparing(ReportProvider::title))
                .map(p -> new ReportDescriptor(p.code(), p.title(), p.description(), formats, p.additionalPermission()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ReportTable build(String code, ReportParams params) {
        ReportProvider provider = providers.get(code);
        if (provider == null) {
            throw new ResourceNotFoundException("Report '" + code + "' does not exist");
        }
        if (provider.additionalPermission() != null && !SecurityUtils.hasPermission(provider.additionalPermission())) {
            throw new AccessDeniedException("Report requires " + provider.additionalPermission());
        }
        return provider.build(params);
    }

    @Transactional
    public ExportedReport export(String code, ReportParams params, ReportFormat format) {
        ReportTable table = build(code, params);
        ExportedReport exported = exportService.export(table, format);
        auditService.record(AuditAction.EXPORT, "Report", null, code, null, Map.of("format", format.name(), "rows", table.rows().size()),
                "Report " + table.title() + " exported as " + format);
        return exported;
    }
}
