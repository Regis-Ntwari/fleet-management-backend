package com.limoz.fleet.reporting;

import com.limoz.fleet.reporting.export.CsvReportExporter;
import com.limoz.fleet.reporting.export.ExcelReportExporter;
import com.limoz.fleet.reporting.export.PdfReportExporter;
import com.limoz.fleet.reporting.export.ReportTable;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ReportExportersTest {

    private final ReportTable table = ReportTable.builder("daily-fleet", "Daily Fleet Report")
            .meta("Date", LocalDate.of(2026, 6, 4))
            .text("plate", "Plate").text("category", "Category").number("km", "Distance (km)").number("cost", "Cost")
            .row("RAD 408 C", "Minibus", 182, new BigDecimal("97960.00"))
            .row("RAD 112 B", "Coaster", 0, BigDecimal.ZERO)
            .totals("Total", "", 182, new BigDecimal("97960.00"))
            .build();

    @Test
    void csvContainsHeaderRowsAndTotals() {
        String csv = new String(new CsvReportExporter().export(table), StandardCharsets.UTF_8);
        assertThat(csv).contains("Plate,Category,Distance (km),Cost")
                .contains("RAD 408 C,Minibus,182,97960")
                .contains("Total,,182,97960");
    }

    @Test
    void excelHasTitleHeaderAndNumericCells() throws Exception {
        byte[] bytes = new ExcelReportExporter().export(table);
        try (var wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Daily Fleet Report");
            assertThat(sheet.getRow(3).getCell(0).getStringCellValue()).isEqualTo("Plate");
            assertThat(sheet.getRow(4).getCell(2).getNumericCellValue()).isEqualTo(182d);
        }
    }

    @Test
    void pdfIsProduced() {
        byte[] bytes = new PdfReportExporter().export(table);
        assertThat(bytes.length).isGreaterThan(500);
        assertThat(new String(bytes, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }
}
