package com.limoz.fleet.booking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Pure pricing arithmetic for bookings and deployment vouchers. */
public final class BookingCalculations {

    private static final BigDecimal MILLIS_PER_DAY = BigDecimal.valueOf(Duration.ofDays(1).toMillis());

    private BookingCalculations() {}

    /** Inclusive number of calendar days between two dates (same day = 1). */
    public static int inclusiveDays(LocalDate start, LocalDate end) {
        return (int) ChronoUnit.DAYS.between(start, end) + 1;
    }

    /**
     * Billable units of a line: calendar days for daily pricing, started months for monthly pricing,
     * a single unit for per-trip and per-km pricing (kilometres are billed from the voucher readings).
     */
    public static long billableUnits(PricingType type, LocalDate start, LocalDate end) {
        return switch (type) {
            case FULL_DAY, HALF_DAY -> inclusiveDays(start, end);
            case MONTHLY -> startedMonths(start, end);
            case PER_TRIP, PER_KM -> 1;
        };
    }

    /** Months covered by an inclusive date range, any started month counting as a full month (min 1). */
    public static long startedMonths(LocalDate start, LocalDate end) {
        LocalDate exclusiveEnd = end.plusDays(1);
        long whole = ChronoUnit.MONTHS.between(start, exclusiveEnd);
        boolean remainder = start.plusMonths(whole).isBefore(exclusiveEnd);
        return Math.max(1, whole + (remainder ? 1 : 0));
    }

    /** quantity x unit price x billable units, 2 dp. */
    public static BigDecimal lineTotal(int quantity, BigDecimal unitPrice, long billableUnits) {
        return unitPrice.multiply(BigDecimal.valueOf(quantity)).multiply(BigDecimal.valueOf(billableUnits)).setScale(2, RoundingMode.HALF_UP);
    }

    /** Elapsed days between departure and return as an exact fraction rounded to 3 dp. */
    public static BigDecimal effectiveDays(Instant departedAt, Instant returnedAt) {
        long millis = Math.max(0, Duration.between(departedAt, returnedAt).toMillis());
        return BigDecimal.valueOf(millis).divide(MILLIS_PER_DAY, 3, RoundingMode.HALF_UP);
    }

    /** effective days x day rate, 2 dp. */
    public static BigDecimal institutionAmount(BigDecimal effectiveDays, BigDecimal dayRate) {
        return effectiveDays.multiply(dayRate).setScale(2, RoundingMode.HALF_UP);
    }

    /** Owner settlement: owner amount minus fuel advanced by LIMOZ (may be negative). */
    public static BigDecimal netAmount(BigDecimal ownerAmount, BigDecimal fuelAmount) {
        return ownerAmount.subtract(fuelAmount).setScale(2, RoundingMode.HALF_UP);
    }
}
