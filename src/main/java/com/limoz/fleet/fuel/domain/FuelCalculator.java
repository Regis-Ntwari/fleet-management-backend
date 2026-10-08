package com.limoz.fleet.fuel.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure fuel arithmetic shared by the service and unit tests: cost, consumption, sensor variance and the
 * anomaly decision. Thresholds are passed in by the caller (they come from system settings).
 */
public final class FuelCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private FuelCalculator() {}

    /** All derived figures of one transaction. {@code distanceKm} is null when there is no previous reading. */
    public record Result(BigDecimal totalAmount, BigDecimal distanceKm, BigDecimal consumptionLPer100km, BigDecimal kmPerLitre,
                         BigDecimal varianceLitres, boolean anomaly, String anomalyReason) {}

    public static BigDecimal totalAmount(BigDecimal litres, BigDecimal pricePerLitre) {
        return litres.multiply(pricePerLitre).setScale(2, RoundingMode.HALF_UP);
    }

    /** Distance travelled since the previous reading, or null when unknown. */
    public static BigDecimal distance(long odometerKm, Long previousOdometerKm) {
        return previousOdometerKm == null ? null : BigDecimal.valueOf(odometerKm - previousOdometerKm).setScale(1, RoundingMode.HALF_UP);
    }

    /** Litres per 100 km; null when the distance is unknown or zero. */
    public static BigDecimal consumption(BigDecimal litres, BigDecimal distanceKm) {
        if (distanceKm == null || distanceKm.signum() <= 0) return null;
        return litres.multiply(HUNDRED).divide(distanceKm, 2, RoundingMode.HALF_UP);
    }

    /** Kilometres per litre; null when the distance is unknown or zero. */
    public static BigDecimal kmPerLitre(BigDecimal litres, BigDecimal distanceKm) {
        if (distanceKm == null || distanceKm.signum() <= 0 || litres.signum() <= 0) return null;
        return distanceKm.divide(litres, 2, RoundingMode.HALF_UP);
    }

    /** Station litres minus sensor-detected litres; null when no sensor value was captured. */
    public static BigDecimal variance(BigDecimal litres, BigDecimal sensorDetectedLitres) {
        return sensorDetectedLitres == null ? null : litres.subtract(sensorDetectedLitres).setScale(2, RoundingMode.HALF_UP);
    }

    public static Result compute(BigDecimal litres, BigDecimal pricePerLitre, long odometerKm, Long previousOdometerKm,
                                 BigDecimal sensorDetectedLitres, BigDecimal highConsumptionThreshold, BigDecimal varianceTolerance) {
        BigDecimal total = totalAmount(litres, pricePerLitre);
        BigDecimal distance = distance(odometerKm, previousOdometerKm);
        BigDecimal consumption = consumption(litres, distance);
        BigDecimal kmPerLitre = kmPerLitre(litres, distance);
        BigDecimal variance = variance(litres, sensorDetectedLitres);

        StringBuilder reason = new StringBuilder();
        if (consumption != null && consumption.compareTo(highConsumptionThreshold) > 0) {
            reason.append("Consumption ").append(consumption.toPlainString()).append(" L/100km exceeds threshold ")
                    .append(highConsumptionThreshold.stripTrailingZeros().toPlainString()).append(" L/100km");
        }
        if (variance != null && variance.abs().compareTo(varianceTolerance) > 0) {
            if (!reason.isEmpty()) reason.append("; ");
            reason.append("Sensor variance ").append(variance.toPlainString()).append(" L exceeds tolerance ")
                    .append(varianceTolerance.stripTrailingZeros().toPlainString()).append(" L");
        }
        boolean anomaly = !reason.isEmpty();
        return new Result(total, distance, consumption, kmPerLitre, variance, anomaly, anomaly ? truncate(reason.toString()) : null);
    }

    private static String truncate(String value) {
        return value.length() > 255 ? value.substring(0, 255) : value;
    }
}
