package com.limoz.fleet.vehicle;

import com.limoz.fleet.vehicle.dto.OdometerLogResponse;
import com.limoz.fleet.vehicle.dto.VehicleCategoryResponse;
import com.limoz.fleet.vehicle.dto.VehicleResponse;
import com.limoz.fleet.vehicle.dto.VehicleSummary;
import org.springframework.stereotype.Component;

@Component
public class VehicleMapper {

    public VehicleResponse toResponse(Vehicle v) {
        return new VehicleResponse(v.getId(), v.getPlateNumber(), v.getFleetNumber(), v.getMake(), v.getModel(), v.getModelYear(),
                v.getCategory().getId(), v.getCategory().getName(), v.getBodyType(), v.getFuelType(), v.getTransmission(),
                v.getEngineNumber(), v.getChassisNumber(), v.getColor(), v.getOdometerKm(), v.getSeatingCapacity(),
                v.getPurchaseDate(), v.getAcquisitionCost(), v.getOwnershipType(), v.getOwnerName(), v.getOwnerContact(),
                v.getOwnerDriverName(), v.getInsuranceProvider(), v.getInsurancePolicyNumber(), v.getInsuranceExpiryDate(),
                v.getDayRate(), v.getCurrentDriver() == null ? null : v.getCurrentDriver().getId(),
                v.getCurrentDriver() == null ? null : v.getCurrentDriver().getFullName(),
                v.getOperationalStatus(), v.getMaintenanceStatus(), v.getDepartment(), v.getNotes(), v.isArchived(),
                v.getCreatedAt(), v.getUpdatedAt(), v.getCreatedBy(), v.getUpdatedBy());
    }

    public VehicleSummary toSummary(Vehicle v) {
        return v == null ? null : new VehicleSummary(v.getId(), v.getPlateNumber(), v.getFleetNumber(), v.getMake(), v.getModel(),
                v.getCategory().getName(), v.getOperationalStatus());
    }

    public VehicleCategoryResponse toResponse(VehicleCategory c) {
        return new VehicleCategoryResponse(c.getId(), c.getCode(), c.getName(), c.getDescription(), c.getMinSeats(), c.getMaxSeats(),
                c.getDefaultDayRate(), c.isActive(), c.getSortOrder());
    }

    public OdometerLogResponse toResponse(OdometerLog log) {
        return new OdometerLogResponse(log.getId(), log.getReadingKm(), log.getPreviousKm(), log.getSource(), log.getReferenceType(),
                log.getReferenceId(), log.getCorrectionReason(), log.getRecordedAt(), log.getRecordedBy());
    }
}
