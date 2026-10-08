package com.limoz.fleet.common.util;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Converts operational (Africa/Kigali) business dates to UTC instant ranges for querying.
 */
public final class DateRanges {

    private DateRanges() {}

    public record InstantRange(Instant from, Instant to) {}

    public static InstantRange forDate(LocalDate date, ZoneId zone) {
        return new InstantRange(date.atStartOfDay(zone).toInstant(), date.plusDays(1).atStartOfDay(zone).toInstant());
    }

    public static InstantRange between(LocalDate from, LocalDate to, ZoneId zone) {
        return new InstantRange(from.atStartOfDay(zone).toInstant(), to.plusDays(1).atStartOfDay(zone).toInstant());
    }

    public static LocalDate today(Clock clock) {
        return LocalDate.now(clock);
    }

    public static LocalDate toLocalDate(Instant instant, ZoneId zone) {
        return instant == null ? null : instant.atZone(zone).toLocalDate();
    }
}
