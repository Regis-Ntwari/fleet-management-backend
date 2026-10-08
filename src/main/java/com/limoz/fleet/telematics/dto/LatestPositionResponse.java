package com.limoz.fleet.telematics.dto;

import com.limoz.fleet.telematics.GpsStatus;
import com.limoz.fleet.vehicle.VehicleStatus;

import java.math.BigDecimal;
import java.time.Instant;

/** Live map row: last known fix of a vehicle with its age. */
public record LatestPositionResponse(
        Long vehicleId,
        String plateNumber,
        String fleetNumber,
        VehicleStatus operationalStatus,
        GpsStatus gpsStatus,
        Instant recordedAt,
        long ageMinutes,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal speedKph,
        BigDecimal heading,
        Boolean ignitionOn,
        BigDecimal odometerKm) {}
