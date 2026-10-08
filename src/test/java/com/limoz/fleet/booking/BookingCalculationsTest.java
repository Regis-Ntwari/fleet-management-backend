package com.limoz.fleet.booking;

import com.limoz.fleet.booking.domain.BookingCalculations;
import com.limoz.fleet.booking.domain.PricingType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class BookingCalculationsTest {

    private final LocalDate jun10 = LocalDate.of(2026, 6, 10);

    @Test
    @DisplayName("daily pricing bills the inclusive number of days, per-trip and per-km bill one unit")
    void billableUnits() {
        assertThat(BookingCalculations.billableUnits(PricingType.FULL_DAY, jun10, jun10.plusDays(4))).isEqualTo(5);
        assertThat(BookingCalculations.billableUnits(PricingType.HALF_DAY, jun10, jun10)).isEqualTo(1);
        assertThat(BookingCalculations.billableUnits(PricingType.PER_TRIP, jun10, jun10.plusDays(30))).isEqualTo(1);
        assertThat(BookingCalculations.billableUnits(PricingType.PER_KM, jun10, jun10.plusDays(30))).isEqualTo(1);
    }

    @Test
    @DisplayName("monthly pricing counts every started month")
    void startedMonths() {
        assertThat(BookingCalculations.billableUnits(PricingType.MONTHLY, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30))).isEqualTo(1);
        assertThat(BookingCalculations.billableUnits(PricingType.MONTHLY, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 15))).isEqualTo(2);
        assertThat(BookingCalculations.billableUnits(PricingType.MONTHLY, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31))).isEqualTo(3);
        assertThat(BookingCalculations.billableUnits(PricingType.MONTHLY, jun10, jun10)).isEqualTo(1);
    }

    @Test
    @DisplayName("line total = quantity x unit price x units")
    void lineTotal() {
        assertThat(BookingCalculations.lineTotal(2, new BigDecimal("800000"), 3)).isEqualByComparingTo("4800000.00");
    }

    @Test
    @DisplayName("effective days are the exact elapsed fraction to 3 decimals and price the institution amount")
    void voucherAmounts() {
        Instant out = Instant.parse("2026-06-10T06:00:00Z");
        BigDecimal days = BookingCalculations.effectiveDays(out, out.plusSeconds(36 * 3600));
        assertThat(days).isEqualByComparingTo("1.500");
        assertThat(BookingCalculations.effectiveDays(out, out.plusSeconds(8 * 3600))).isEqualByComparingTo("0.333");
        assertThat(BookingCalculations.effectiveDays(out, out)).isEqualByComparingTo("0.000");
        assertThat(BookingCalculations.institutionAmount(days, new BigDecimal("160000"))).isEqualByComparingTo("240000.00");
        assertThat(BookingCalculations.netAmount(new BigDecimal("100000"), new BigDecimal("35000"))).isEqualByComparingTo("65000.00");
    }
}
