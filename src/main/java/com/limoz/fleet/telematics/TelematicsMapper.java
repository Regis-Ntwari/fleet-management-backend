package com.limoz.fleet.telematics;

import com.limoz.fleet.telematics.dto.DeviceResponse;
import com.limoz.fleet.telematics.dto.LatestPositionResponse;
import com.limoz.fleet.telematics.dto.PositionResponse;
import com.limoz.fleet.vehicle.Vehicle;
import com.limoz.fleet.vehicle.VehicleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class TelematicsMapper {

    private final VehicleMapper vehicleMapper;
    private final Clock clock;

    public DeviceResponse toResponse(TelematicsDevice d) {
        Instant now = Instant.now(clock);
        boolean gpsProblem = d.getGpsStatus().isProblem();
        boolean fuelProblem = d.getFuelSensorStatus().isProblem();
        return new DeviceResponse(d.getId(), vehicleMapper.toSummary(d.getVehicle()), d.getProviderCode(), d.getExternalDeviceId(),
                d.getSimNumber(), d.getInstalledAt(), d.isActive(), d.getGpsStatus(), d.getFuelSensorStatus(),
                gpsProblem, fuelProblem, d.isActive() && !gpsProblem && !fuelProblem,
                d.getLastCommunicationAt(), minutesSince(d.getLastCommunicationAt(), now),
                d.getLastLatitude(), d.getLastLongitude(), d.getLastSpeedKph(), d.getLastOdometerKm(), d.getLastIgnitionOn(),
                d.getLastBatteryVoltage(), d.getNotes(), d.getCreatedAt(), d.getUpdatedAt());
    }

    public PositionResponse toResponse(VehiclePosition p) {
        return new PositionResponse(p.getId(), p.getVehicleId(), p.getDeviceId(), p.getRecordedAt(), p.getLatitude(), p.getLongitude(),
                p.getSpeedKph(), p.getHeading(), p.getOdometerKm(), p.getIgnitionOn(), p.getBatteryVoltage(), p.getFuelLevelLitres(),
                p.getSource());
    }

    public LatestPositionResponse toLatest(VehiclePosition p, Vehicle vehicle, GpsStatus gpsStatus) {
        return new LatestPositionResponse(vehicle.getId(), vehicle.getPlateNumber(), vehicle.getFleetNumber(), vehicle.getOperationalStatus(),
                gpsStatus, p.getRecordedAt(), minutesSince(p.getRecordedAt(), Instant.now(clock)), p.getLatitude(), p.getLongitude(),
                p.getSpeedKph(), p.getHeading(), p.getIgnitionOn(), p.getOdometerKm());
    }

    private static Long minutesSince(Instant at, Instant now) {
        return at == null ? null : Math.max(0, Duration.between(at, now).toMinutes());
    }
}
