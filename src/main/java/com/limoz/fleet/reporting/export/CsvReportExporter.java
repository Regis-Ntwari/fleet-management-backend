package com.limoz.fleet.reporting.export;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

@Component
public class CsvReportExporter implements ReportExporter {

    @Override
    public ReportFormat format() {
        return ReportFormat.CSV;
    }

    @Override
    public byte[] export(ReportTable table) {
        StringWriter writer = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT)) {
            printer.printRecord(table.columns().stream().map(ReportTable.Column::label).toList());
            for (var row : table.rows()) {
                printer.printRecord(row.stream().map(ReportValues::plain).toList());
            }
            if (table.totals() != null) {
                printer.printRecord(table.totals().stream().map(ReportValues::plain).toList());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        // UTF-8 BOM so Excel opens accented text correctly
        byte[] body = writer.toString().getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[body.length + 3];
        out[0] = (byte) 0xEF; out[1] = (byte) 0xBB; out[2] = (byte) 0xBF;
        System.arraycopy(body, 0, out, 3, body.length);
        return out;
    }
}
