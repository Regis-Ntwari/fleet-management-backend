package com.limoz.fleet.telematics.dto;

import com.limoz.fleet.telematics.FuelSensorStatus;
import com.limoz.fleet.telematics.GpsStatus;
import com.limoz.fleet.vehicle.dto.VehicleSummary;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Telematics device with its vehicle and a health summary. */
public record DeviceResponse(
        Long id,
        VehicleSummary vehicle,
        String providerCode,
        String externalDeviceId,
        String simNumber,
        LocalDate installedAt,
        boolean active,
        GpsStatus gpsStatus,
        FuelSensorStatus fuelSensorStatus,
        boolean gpsProblem,
        boolean fuelSensorProblem,
        boolean healthy,
        Instant lastCommunicationAt,
        Long minutesSinceLastCommunication,
        BigDecimal lastLatitude,
        BigDecimal lastLongitude,
        BigDecimal lastSpeedKph,
        Long lastOdometerKm,
        Boolean lastIgnitionOn,
        BigDecimal lastBatteryVoltage,
        String notes,
        Instant createdAt,
        Instant updatedAt) {}
