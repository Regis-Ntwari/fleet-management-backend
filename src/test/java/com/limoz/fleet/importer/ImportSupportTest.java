package com.limoz.fleet.importer;

import com.limoz.fleet.vehicle.FuelType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImportSupportTest {

    @Test
    void parsesCommonDateFormats() {
        assertThat(ImportSupport.parseDate("2026-06-04", "d")).isEqualTo(LocalDate.of(2026, 6, 4));
        assertThat(ImportSupport.parseDate("04/06/2026", "d")).isEqualTo(LocalDate.of(2026, 6, 4));
        assertThat(ImportSupport.parseDate("4 Jun 2026", "d")).isEqualTo(LocalDate.of(2026, 6, 4));
        assertThat(ImportSupport.parseDate("", "d")).isNull();
        assertThatThrownBy(() -> ImportSupport.parseDate("June 4th", "purchase_date"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("purchase_date");
    }

    @Test
    void parsesNumbersWithThousandSeparators() {
        assertThat(ImportSupport.parseDecimal("1,580.50", "price")).isEqualByComparingTo(new BigDecimal("1580.50"));
        assertThat(ImportSupport.parseLong("182 600", "odometer")).isEqualTo(182600L);
        assertThatThrownBy(() -> ImportSupport.parseInt("abc", "year")).hasMessageContaining("year");
    }

    @Test
    void parsesEnumsLeniently() {
        assertThat(ImportSupport.parseEnum("diesel", FuelType.class, "fuel", null)).isEqualTo(FuelType.DIESEL);
        assertThat(ImportSupport.parseEnum(null, FuelType.class, "fuel", FuelType.PETROL)).isEqualTo(FuelType.PETROL);
        assertThatThrownBy(() -> ImportSupport.parseEnum("kerosene", FuelType.class, "fuel_type", null))
                .hasMessageContaining("fuel_type");
    }

    @Test
    void normalisesHeaders() {
        assertThat(TabularFileReader.normalise(" Plate Number ")).isEqualTo("plate_number");
        assertThat(TabularFileReader.normalise("LICENCE-NO.")).isEqualTo("licence_no");
    }
}
