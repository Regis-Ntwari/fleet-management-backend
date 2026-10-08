package com.limoz.fleet.notification.alert.scanner.service;

import com.limoz.fleet.notification.domain.NotificationSeverity;
import com.limoz.fleet.notification.alert.domain.AlertCandidate;
import com.limoz.fleet.notification.alert.service.AlertScanner;
import com.limoz.fleet.notification.alert.domain.AlertType;
import com.limoz.fleet.vehicle.domain.Vehicle;
import com.limoz.fleet.vehicle.repository.VehicleRepository;
import com.limoz.fleet.vehicle.domain.VehicleStatus;
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
