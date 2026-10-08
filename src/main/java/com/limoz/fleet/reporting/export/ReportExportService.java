package com.limoz.fleet.reporting.export;

import com.limoz.fleet.common.exception.BusinessRuleException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReportExportService {

    private final Map<ReportFormat, ReportExporter> exporters;
    private final Clock clock;

    public ReportExportService(List<ReportExporter> exporters, Clock clock) {
        this.exporters = exporters.stream().collect(Collectors.toMap(ReportExporter::format, Function.identity()));
        this.clock = clock;
    }

    public ExportedReport export(ReportTable table, ReportFormat format) {
        ReportExporter exporter = exporters.get(format);
        if (exporter == null) {
            throw new BusinessRuleException("UNSUPPORTED_FORMAT", "Format " + format + " is not supported for export");
        }
        String fileName = table.code() + "-" + LocalDate.now(clock) + "." + format.extension();
        return new ExportedReport(fileName, format.contentType(), exporter.export(table));
    }
}
