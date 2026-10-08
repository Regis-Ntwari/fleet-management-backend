package com.limoz.fleet.fuel;

import com.limoz.fleet.fuel.domain.FuelCalculator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class FuelCalculationTest {

    private static final BigDecimal HIGH_CONSUMPTION = new BigDecimal("25");
    private static final BigDecimal VARIANCE_TOLERANCE = new BigDecimal("5");

    @Test
    @DisplayName("total cost = litres x price per litre, rounded half-up to 2 decimals")
    void totalCost() {
        assertThat(FuelCalculator.totalAmount(new BigDecimal("40"), new BigDecimal("1580"))).isEqualByComparingTo("63200.00");
        assertThat(FuelCalculator.totalAmount(new BigDecimal("33.333"), new BigDecimal("1580.50"))).isEqualByComparingTo("52682.81");
        assertThat(FuelCalculator.totalAmount(new BigDecimal("0.005"), new BigDecimal("1"))).isEqualByComparingTo("0.01");
    }

    @Test
    @DisplayName("consumption and km per litre are derived from the distance since the previous reading")
    void consumptionAndEfficiency() {
        BigDecimal distance = FuelCalculator.distance(15800, 15300L);
        assertThat(distance).isEqualByComparingTo("500.0");
        assertThat(FuelCalculator.consumption(new BigDecimal("60"), distance)).isEqualByComparingTo("12.00");
        assertThat(FuelCalculator.kmPerLitre(new BigDecimal("60"), distance)).isEqualByComparingTo("8.33");
        assertThat(FuelCalculator.consumption(new BigDecimal("45.5"), new BigDecimal("350"))).isEqualByComparingTo("13.00");
    }

    @Test
    @DisplayName("without a previous reading or with zero distance there is no consumption figure")
    void noDistance() {
        assertThat(FuelCalculator.distance(15800, null)).isNull();
        assertThat(FuelCalculator.consumption(new BigDecimal("60"), null)).isNull();
        assertThat(FuelCalculator.consumption(new BigDecimal("60"), new BigDecimal("0.0"))).isNull();
        assertThat(FuelCalculator.kmPerLitre(new BigDecimal("60"), new BigDecimal("0.0"))).isNull();
        FuelCalculator.Result result = FuelCalculator.compute(new BigDecimal("60"), new BigDecimal("1580"), 15800, null, null,
                HIGH_CONSUMPTION, VARIANCE_TOLERANCE);
        assertThat(result.distanceKm()).isNull();
        assertThat(result.consumptionLPer100km()).isNull();
        assertThat(result.anomaly()).isFalse();
        assertThat(result.anomalyReason()).isNull();
    }

    @Test
    @DisplayName("sensor variance = station litres - sensor litres")
    void variance() {
        assertThat(FuelCalculator.variance(new BigDecimal("60"), new BigDecimal("50"))).isEqualByComparingTo("10.00");
        assertThat(FuelCalculator.variance(new BigDecimal("60"), new BigDecimal("62.5"))).isEqualByComparingTo("-2.50");
        assertThat(FuelCalculator.variance(new BigDecimal("60"), null)).isNull();
    }

    @Test
    @DisplayName("an anomaly is raised when consumption exceeds the threshold")
    void highConsumptionAnomaly() {
        FuelCalculator.Result normal = FuelCalculator.compute(new BigDecimal("60"), new BigDecimal("1580"), 15800, 15300L, null,
                HIGH_CONSUMPTION, VARIANCE_TOLERANCE);
        assertThat(normal.anomaly()).isFalse();

        FuelCalculator.Result high = FuelCalculator.compute(new BigDecimal("60"), new BigDecimal("1580"), 15400, 15300L, null,
                HIGH_CONSUMPTION, VARIANCE_TOLERANCE);
        assertThat(high.consumptionLPer100km()).isEqualByComparingTo("60.00");
        assertThat(high.anomaly()).isTrue();
        assertThat(high.anomalyReason()).contains("Consumption 60.00 L/100km exceeds threshold 25 L/100km");

        FuelCalculator.Result exactlyAtThreshold = FuelCalculator.compute(new BigDecimal("25"), new BigDecimal("1580"), 15400, 15300L, null,
                HIGH_CONSUMPTION, VARIANCE_TOLERANCE);
        assertThat(exactlyAtThreshold.anomaly()).as("threshold itself is not an anomaly").isFalse();
    }

    @Test
    @DisplayName("an anomaly is raised when the absolute sensor variance exceeds the tolerance, and both reasons are reported")
    void varianceAnomaly() {
        FuelCalculator.Result tooMuch = FuelCalculator.compute(new BigDecimal("60"), new BigDecimal("1580"), 15800, 15300L, new BigDecimal("50"),
                HIGH_CONSUMPTION, VARIANCE_TOLERANCE);
        assertThat(tooMuch.anomaly()).isTrue();
        assertThat(tooMuch.varianceLitres()).isEqualByComparingTo("10.00");
        assertThat(tooMuch.anomalyReason()).contains("Sensor variance 10.00 L exceeds tolerance 5 L");

        FuelCalculator.Result negative = FuelCalculator.compute(new BigDecimal("60"), new BigDecimal("1580"), 15800, 15300L, new BigDecimal("66"),
                HIGH_CONSUMPTION, VARIANCE_TOLERANCE);
        assertThat(negative.anomaly()).as("negative variance counts by absolute value").isTrue();

        FuelCalculator.Result withinTolerance = FuelCalculator.compute(new BigDecimal("60"), new BigDecimal("1580"), 15800, 15300L, new BigDecimal("56"),
                HIGH_CONSUMPTION, VARIANCE_TOLERANCE);
        assertThat(withinTolerance.anomaly()).isFalse();

        FuelCalculator.Result both = FuelCalculator.compute(new BigDecimal("60"), new BigDecimal("1580"), 15400, 15300L, new BigDecimal("40"),
                HIGH_CONSUMPTION, VARIANCE_TOLERANCE);
        assertThat(both.anomalyReason()).contains("Consumption").contains("; ").contains("Sensor variance");
    }
}
