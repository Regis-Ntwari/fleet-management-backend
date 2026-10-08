package com.limoz.fleet.reporting.export.domain;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Consistent rendering of report cell values across formats. */
public final class ReportValues {

    private static final ZoneId ZONE = ZoneId.of("Africa/Kigali");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.ENGLISH);
    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.ENGLISH));

    private ReportValues() {}

    public static String plain(Object value) {
        if (value == null) return "";
        if (value instanceof BigDecimal d) return d.stripTrailingZeros().toPlainString();
        if (value instanceof LocalDate d) return d.toString();
        if (value instanceof Instant i) return i.atZone(ZONE).toLocalDateTime().withNano(0).toString();
        if (value instanceof Enum<?> e) return e.name();
        return String.valueOf(value);
    }

    public static String display(Object value) {
        if (value == null) return "";
        if (value instanceof BigDecimal d) return MONEY.format(d);
        if (value instanceof Double || value instanceof Float) return MONEY.format(((Number) value).doubleValue());
        if (value instanceof Number n) return MONEY.format(n);
        if (value instanceof LocalDate d) return d.format(DATE);
        if (value instanceof Instant i) return i.atZone(ZONE).format(DATE_TIME);
        if (value instanceof Enum<?> e) return e.name().replace('_', ' ');
        return String.valueOf(value);
    }

    public static boolean isNumeric(Object value) {
        return value instanceof Number;
    }
}
