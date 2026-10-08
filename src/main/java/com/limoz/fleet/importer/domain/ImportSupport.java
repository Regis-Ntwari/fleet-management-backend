package com.limoz.fleet.importer.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/** Lenient parsers for values typed by humans in spreadsheets. */
public final class ImportSupport {

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"));

    private ImportSupport() {}

    public static LocalDate parseDate(String value, String field) {
        if (value == null || value.isBlank()) return null;
        for (DateTimeFormatter f : DATE_FORMATS) {
            try {
                return LocalDate.parse(value.trim(), f);
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        throw new IllegalArgumentException(field + " '" + value + "' is not a valid date (use yyyy-MM-dd or dd/MM/yyyy)");
    }

    public static BigDecimal parseDecimal(String value, String field) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value.replace(",", "").replace(" ", "").trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " '" + value + "' is not a number");
        }
    }

    public static Long parseLong(String value, String field) {
        BigDecimal d = parseDecimal(value, field);
        return d == null ? null : d.longValue();
    }

    public static Integer parseInt(String value, String field) {
        BigDecimal d = parseDecimal(value, field);
        return d == null ? null : d.intValue();
    }

    public static <E extends Enum<E>> E parseEnum(String value, Class<E> type, String field, E defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        String normalised = value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equals(normalised)) return constant;
        }
        throw new IllegalArgumentException(field + " '" + value + "' is not one of " + List.of(type.getEnumConstants()));
    }

    public static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
