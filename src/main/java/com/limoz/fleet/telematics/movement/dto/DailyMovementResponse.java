package com.limoz.fleet.telematics.movement.dto;

import com.limoz.fleet.telematics.movement.MovementDataSource;
import com.limoz.fleet.telematics.movement.MovementFlag;
import com.limoz.fleet.vehicle.dto.VehicleSummary;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record DailyMovementResponse(
        Long id,
        VehicleSummary vehicle,
        LocalDate summaryDate,
        BigDecimal distanceKm,
        boolean moved,
        Instant firstMovementAt,
        Instant lastMovementAt,
        int drivingMinutes,
        int idleMinutes,
        int nightDrivingMinutes,
        BigDecimal maxSpeedKph,
        int tripsCount,
        BigDecimal startLatitude,
        BigDecimal startLongitude,
        BigDecimal endLatitude,
        BigDecimal endLongitude,
        String startLocation,
        String endLocation,
        boolean gpsIssue,
        List<MovementFlag> flags,
        MovementDataSource dataSource,
        Instant computedAt) {}
