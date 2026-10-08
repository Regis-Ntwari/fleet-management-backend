package com.limoz.fleet.fuel;

import com.limoz.fleet.driver.DriverMapper;
import com.limoz.fleet.fuel.dto.FuelSummaryResponse;
import com.limoz.fleet.fuel.dto.FuelTransactionResponse;
import com.limoz.fleet.vehicle.VehicleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class FuelMapper {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final VehicleMapper vehicleMapper;
    private final DriverMapper driverMapper;

    public FuelTransactionResponse toResponse(FuelTransaction f) {
        return new FuelTransactionResponse(f.getId(), vehicleMapper.toSummary(f.getVehicle()), driverMapper.toSummary(f.getDriver()),
                f.getTripId(), f.getBookingSlotId(), f.getTransactionAt(), f.getStationName(), f.getSupplierName(), f.getFuelType(),
                f.getLitres(), f.getPricePerLitre(), f.getTotalAmount(), f.getCurrency(), f.getOdometerKm(), f.getPreviousOdometerKm(),
                f.getDistanceSinceLastKm(), f.getConsumptionLPer100km(), f.getKmPerLitre(), f.getSensorDetectedLitres(),
                f.getVarianceLitres(), f.isAnomaly(), f.getAnomalyReason(), f.isFullTank(), f.getReceiptNumber(),
                f.getReceiptAttachmentId(), f.getPaymentMethod(), f.getEnteredByUserId(), f.getNotes(), f.isArchived(),
                f.getCreatedAt(), f.getUpdatedAt(), f.getCreatedBy(), f.getUpdatedBy());
    }

    /** Builds a summary from database aggregates; averages are derived here from the sums (no row iteration). */
    public FuelSummaryResponse toSummary(FuelTransactionRepository.Totals t, Long vehicleId, String plate, String vehicleName,
                                         LocalDate from, LocalDate to) {
        BigDecimal litres = nz(t.getTotalLitres());
        BigDecimal cost = nz(t.getTotalCost());
        BigDecimal distance = nz(t.getTotalDistanceKm());
        BigDecimal litresWithDistance = nz(t.getLitresWithDistance());
        BigDecimal consumption = distance.signum() > 0
                ? litresWithDistance.multiply(HUNDRED).divide(distance, 2, RoundingMode.HALF_UP) : null;
        BigDecimal kmPerLitre = litresWithDistance.signum() > 0 && distance.signum() > 0
                ? distance.divide(litresWithDistance, 2, RoundingMode.HALF_UP) : null;
        BigDecimal avgPrice = litres.signum() > 0 ? cost.divide(litres, 2, RoundingMode.HALF_UP) : null;
        return new FuelSummaryResponse(vehicleId, plate, vehicleName, from, to, t.getTransactionCount(),
                litres.setScale(2, RoundingMode.HALF_UP), cost.setScale(2, RoundingMode.HALF_UP),
                distance.setScale(1, RoundingMode.HALF_UP), consumption, kmPerLitre, avgPrice, t.getAnomalyCount());
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
