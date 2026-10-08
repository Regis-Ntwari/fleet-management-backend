package com.limoz.fleet.notification.alert.scanner;

import com.limoz.fleet.notification.NotificationSeverity;
import com.limoz.fleet.notification.alert.AlertCandidate;
import com.limoz.fleet.notification.alert.AlertScanner;
import com.limoz.fleet.notification.alert.AlertType;
import com.limoz.fleet.vehicle.Vehicle;
import com.limoz.fleet.vehicle.VehicleRepository;
import com.limoz.fleet.vehicle.VehicleStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** Non-archived vehicles that are OUT_OF_SERVICE -> WARNING (capacity lost until they return). */
@Component
@RequiredArgsConstructor
public class VehicleOutOfServiceScanner implements AlertScanner {

    private final VehicleRepository vehicleRepository;

    @Override
    public List<AlertCandidate> scan() {
        return vehicleRepository.findByArchivedFalseAndOperationalStatusInOrderByPlateNumberAsc(List.of(VehicleStatus.OUT_OF_SERVICE))
                .stream().map(VehicleOutOfServiceScanner::candidate).toList();
    }

    private static AlertCandidate candidate(Vehicle v) {
        return new AlertCandidate(AlertType.VEHICLE_OUT_OF_SERVICE, NotificationSeverity.WARNING,
                "Out of service: " + v.getPlateNumber(),
                v.getDisplayName() + " is out of service and cannot be dispatched",
                "Vehicle", v.getId(), v.getPlateNumber(), "/vehicles/" + v.getId(),
                AlertCandidate.key(AlertType.VEHICLE_OUT_OF_SERVICE, "Vehicle", v.getId(), null));
    }
}
