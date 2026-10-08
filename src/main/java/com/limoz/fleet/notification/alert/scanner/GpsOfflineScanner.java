package com.limoz.fleet.notification.alert.scanner;

import com.limoz.fleet.notification.NotificationSeverity;
import com.limoz.fleet.notification.alert.AlertCandidate;
import com.limoz.fleet.notification.alert.AlertScanner;
import com.limoz.fleet.notification.alert.AlertType;
import com.limoz.fleet.telematics.FuelSensorStatus;
import com.limoz.fleet.telematics.GpsStatus;
import com.limoz.fleet.telematics.TelematicsDevice;
import com.limoz.fleet.telematics.TelematicsDeviceRepository;
import com.limoz.fleet.vehicle.Vehicle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Devices that are OFFLINE / NO_SIGNAL / DISCONNECTED -> CRITICAL; faulty fuel sensors -> WARNING. */
@Component
@RequiredArgsConstructor
public class GpsOfflineScanner implements AlertScanner {

    private final TelematicsDeviceRepository deviceRepository;

    @Override
    public List<AlertCandidate> scan() {
        List<GpsStatus> gpsProblems = Arrays.stream(GpsStatus.values()).filter(GpsStatus::isProblem).toList();
        List<FuelSensorStatus> fuelProblems = Arrays.stream(FuelSensorStatus.values()).filter(FuelSensorStatus::isProblem).toList();
        List<AlertCandidate> out = new ArrayList<>();
        for (TelematicsDevice d : deviceRepository.findProblems(gpsProblems, fuelProblems)) {
            Vehicle v = d.getVehicle();
            String link = "/vehicles/" + v.getId();
            if (d.getGpsStatus().isProblem()) {
                String detail = switch (d.getGpsStatus()) {
                    case OFFLINE -> "no communication since " + d.getLastCommunicationAt();
                    case NO_SIGNAL -> "the device has never communicated";
                    case DISCONNECTED -> "the device is deactivated";
                    default -> d.getGpsStatus().name();
                };
                out.add(new AlertCandidate(AlertType.GPS_OFFLINE, NotificationSeverity.CRITICAL,
                        "GPS " + d.getGpsStatus().name().toLowerCase().replace('_', ' ') + ": " + v.getPlateNumber(),
                        "GPS device of " + v.getPlateNumber() + " is " + d.getGpsStatus() + " - " + detail,
                        "Vehicle", v.getId(), v.getPlateNumber(), link,
                        AlertCandidate.key(AlertType.GPS_OFFLINE, "Vehicle", v.getId(), null)));
            }
            if (d.getFuelSensorStatus().isProblem()) {
                out.add(new AlertCandidate(AlertType.FUEL_SENSOR_FAULT, NotificationSeverity.WARNING,
                        "Fuel sensor faulty: " + v.getPlateNumber(),
                        "The fuel level sensor of " + v.getPlateNumber() + " is reported FAULTY; fuel readings are unreliable",
                        "Vehicle", v.getId(), v.getPlateNumber(), link,
                        AlertCandidate.key(AlertType.FUEL_SENSOR_FAULT, "Vehicle", v.getId(), null)));
            }
        }
        return out;
    }
}
