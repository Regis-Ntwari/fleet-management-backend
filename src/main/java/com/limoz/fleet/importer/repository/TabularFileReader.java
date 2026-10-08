package com.limoz.fleet.importer.repository;

import com.limoz.fleet.common.exception.BusinessRuleException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads CSV (.csv) or Excel (.xlsx/.xls) uploads into header-keyed rows. Header names are normalised to
 * lower snake_case so "Plate Number", "plate_number" and "PLATE NUMBER" are equivalent.
 */
@Component
public class TabularFileReader {

    public record TabularRow(int rowNumber, Map<String, String> values) {
        public String get(String key) {
            String v = values.get(normalise(key));
            return v == null || v.isBlank() ? null : v.trim();
        }
    }

    public List<TabularRow> read(MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        try (InputStream in = file.getInputStream()) {
            if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
                return readExcel(in);
            }
            if (name.endsWith(".csv") || name.endsWith(".txt") || name.isEmpty()) {
                return readCsv(in);
            }
            throw new BusinessRuleException("UNSUPPORTED_IMPORT_FORMAT", "Only .csv, .xlsx and .xls files can be imported");
        } catch (IOException e) {
            throw new BusinessRuleException("IMPORT_READ_FAILED", "The uploaded file could not be read: " + e.getMessage());
        }
    }

    private List<TabularRow> readCsv(InputStream in) throws IOException {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader().setSkipHeaderRecord(true).setIgnoreEmptyLines(true).setTrim(true)
                .setIgnoreSurroundingSpaces(true).setAllowMissingColumnNames(true).get();
        List<TabularRow> rows = new ArrayList<>();
        try (CSVParser parser = CSVParser.parse(new InputStreamReader(in, StandardCharsets.UTF_8), format)) {
            List<String> headers = parser.getHeaderNames().stream().map(TabularFileReader::normalise).toList();
            for (CSVRecord record : parser) {
                Map<String, String> values = new LinkedHashMap<>();
                for (int i = 0; i < headers.size() && i < record.size(); i++) {
                    values.put(headers.get(i), record.get(i));
                }
                if (values.values().stream().allMatch(v -> v == null || v.isBlank())) continue;
                rows.add(new TabularRow((int) record.getRecordNumber() + 1, values));
            }
        }
        return rows;
    }

    private List<TabularRow> readExcel(InputStream in) throws IOException {
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        DateTimeFormatter iso = DateTimeFormatter.ISO_LOCAL_DATE;
        List<TabularRow> rows = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) return rows;
            List<String> headers = new ArrayList<>();
            for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                Cell cell = headerRow.getCell(c);
                headers.add(cell == null ? "col_" + c : normalise(formatter.formatCellValue(cell)));
            }
            for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                Map<String, String> values = new LinkedHashMap<>();
                boolean empty = true;
                for (int c = 0; c < headers.size(); c++) {
                    Cell cell = row.getCell(c);
                    String value = null;
                    if (cell != null) {
                        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                            value = cell.getLocalDateTimeCellValue().toLocalDate().format(iso);
                        } else if (cell.getCellType() == CellType.NUMERIC) {
                            double d = cell.getNumericCellValue();
                            value = d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
                        } else {
                            value = formatter.formatCellValue(cell);
                        }
                    }
                    if (value != null && !value.isBlank()) empty = false;
                    values.put(headers.get(c), value);
                }
                if (!empty) rows.add(new TabularRow(r + 1, values));
            }
        }
        return rows;
    }

    public static String normalise(String header) {
        return header == null ? "" : header.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    }
}
