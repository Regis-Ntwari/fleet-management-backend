package com.limoz.fleet.reporting.export;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/** Streams large reports to .xlsx without holding every row in memory. */
@Component
public class ExcelReportExporter implements ReportExporter {

    @Override
    public ReportFormat format() {
        return ReportFormat.XLSX;
    }

    @Override
    public byte[] export(ReportTable table) {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(200); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sheetName(table.title()));
            CellStyle titleStyle = workbook.createCellStyle();
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleStyle.setFont(titleFont);
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_GREEN.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            CellStyle numberStyle = workbook.createCellStyle();
            numberStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.##"));
            numberStyle.setAlignment(HorizontalAlignment.RIGHT);
            CellStyle totalStyle = workbook.createCellStyle();
            totalStyle.cloneStyleFrom(numberStyle);
            Font totalFont = workbook.createFont();
            totalFont.setBold(true);
            totalStyle.setFont(totalFont);
            totalStyle.setBorderTop(BorderStyle.THIN);

            int r = 0;
            Row title = sheet.createRow(r++);
            Cell titleCell = title.createCell(0);
            titleCell.setCellValue(table.title());
            titleCell.setCellStyle(titleStyle);
            for (Map.Entry<String, String> meta : table.metadata().entrySet()) {
                Row metaRow = sheet.createRow(r++);
                metaRow.createCell(0).setCellValue(meta.getKey());
                metaRow.createCell(1).setCellValue(meta.getValue());
            }
            r++;
            Row header = sheet.createRow(r++);
            for (int c = 0; c < table.columns().size(); c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(table.columns().get(c).label());
                cell.setCellStyle(headerStyle);
            }
            for (var rowValues : table.rows()) {
                Row row = sheet.createRow(r++);
                for (int c = 0; c < rowValues.size(); c++) {
                    write(row.createCell(c), rowValues.get(c), numberStyle);
                }
            }
            if (table.totals() != null) {
                Row row = sheet.createRow(r++);
                for (int c = 0; c < table.totals().size(); c++) {
                    Cell cell = row.createCell(c);
                    write(cell, table.totals().get(c), totalStyle);
                    cell.setCellStyle(totalStyle);
                }
            }
            for (int c = 0; c < table.columns().size(); c++) {
                sheet.setColumnWidth(c, Math.min(60, Math.max(12, table.columns().get(c).label().length() + 6)) * 256);
            }
            workbook.write(out);
            workbook.dispose();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(Cell cell, Object value, CellStyle numberStyle) {
        switch (value) {
            case null -> cell.setBlank();
            case BigDecimal d -> { cell.setCellValue(d.doubleValue()); cell.setCellStyle(numberStyle); }
            case Number n -> { cell.setCellValue(n.doubleValue()); cell.setCellStyle(numberStyle); }
            case LocalDate d -> cell.setCellValue(ReportValues.display(d));
            case Instant i -> cell.setCellValue(ReportValues.display(i));
            case Boolean b -> cell.setCellValue(b ? "Yes" : "No");
            default -> cell.setCellValue(ReportValues.display(value));
        }
    }

    private static String sheetName(String title) {
        String name = title.replaceAll("[\\\\/*?\\[\\]:]", " ").trim();
        return name.length() > 31 ? name.substring(0, 31) : name;
    }
}
