package com.limoz.fleet.telematics.service;

import com.limoz.fleet.common.exception.BusinessRuleException;
import com.limoz.fleet.telematics.dto.IngestResult;
import com.limoz.fleet.telematics.dto.PositionInput;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Parses the position CSV format: header {@code plate,recordedAt,latitude,longitude,speedKph,odometerKm,ignition}
 * (case-insensitive, extra columns ignored). Rows that cannot be parsed are reported as errors with their
 * row number; they are never silently dropped.
 */
@Component
public class PositionCsvParser {

    public static final String COL_PLATE = "plate";
    public static final String COL_RECORDED_AT = "recordedAt";
    public static final String COL_LATITUDE = "latitude";
    public static final String COL_LONGITUDE = "longitude";
    public static final String COL_SPEED = "speedKph";
    public static final String COL_ODOMETER = "odometerKm";
    public static final String COL_IGNITION = "ignition";

    private static final Set<String> TRUE_VALUES = Set.of("1", "true", "on", "yes", "y");
    private static final Set<String> FALSE_VALUES = Set.of("0", "false", "off", "no", "n");

    /** Parsed rows, in file order, together with the rows that failed to parse. */
    public record ParsedCsv(List<PositionInput> rows, List<IngestResult.RowError> errors) {}

    public ParsedCsv parse(InputStream in, int maxRows) {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreHeaderCase(true)
                .setTrim(true)
                .setIgnoreEmptyLines(true)
                .get();
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
             CSVParser parser = CSVParser.parse(reader, format)) {
            requireColumns(parser);
            List<PositionInput> rows = new ArrayList<>();
            List<IngestResult.RowError> errors = new ArrayList<>();
            int rowNumber = 0;
            for (CSVRecord record : parser) {
                rowNumber++;
                if (rowNumber > maxRows) {
                    throw new BusinessRuleException("CSV_TOO_LARGE", "CSV import is limited to " + maxRows + " rows per file");
                }
                try {
                    rows.add(toInput(record));
                } catch (IllegalArgumentException | DateTimeParseException ex) {
                    rows.add(null);
                    errors.add(new IngestResult.RowError(rowNumber, ex.getMessage()));
                }
            }
            return new ParsedCsv(rows, errors);
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not read CSV file", ex);
        }
    }

    private static void requireColumns(CSVParser parser) {
        List<String> headers = parser.getHeaderNames().stream().map(h -> h.toLowerCase(Locale.ROOT)).toList();
        for (String required : List.of(COL_PLATE, COL_RECORDED_AT, COL_LATITUDE, COL_LONGITUDE)) {
            if (!headers.contains(required.toLowerCase(Locale.ROOT))) {
                throw new BusinessRuleException("CSV_MISSING_COLUMN", "CSV file is missing the required column '" + required + "'");
            }
        }
    }

    private static PositionInput toInput(CSVRecord r) {
        String plate = value(r, COL_PLATE);
        if (plate == null) throw new IllegalArgumentException("plate is required");
        String recordedAt = value(r, COL_RECORDED_AT);
        if (recordedAt == null) throw new IllegalArgumentException("recordedAt is required");
        return new PositionInput(null, plate, null, parseInstant(recordedAt),
                decimal(r, COL_LATITUDE), decimal(r, COL_LONGITUDE), decimal(r, COL_SPEED), null,
                decimal(r, COL_ODOMETER), bool(r, COL_IGNITION), null, null);
    }

    private static Instant parseInstant(String text) {
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(text).toInstant();
            } catch (DateTimeParseException ex) {
                throw new IllegalArgumentException("recordedAt '" + text + "' is not an ISO-8601 timestamp with offset (e.g. 2026-10-08T06:30:00Z)");
            }
        }
    }

    private static String value(CSVRecord r, String column) {
        if (!r.isMapped(column)) return null;
        String v = r.get(column);
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static BigDecimal decimal(CSVRecord r, String column) {
        String v = value(r, column);
        if (v == null) return null;
        try {
            return new BigDecimal(v);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(column + " '" + v + "' is not a number");
        }
    }

    private static Boolean bool(CSVRecord r, String column) {
        String v = value(r, column);
        if (v == null) return null;
        String lower = v.toLowerCase(Locale.ROOT);
        if (TRUE_VALUES.contains(lower)) return Boolean.TRUE;
        if (FALSE_VALUES.contains(lower)) return Boolean.FALSE;
        throw new IllegalArgumentException(column + " '" + v + "' must be true/false, on/off or 1/0");
    }
}
