package com.limoz.fleet.telematics.movement.dto;

import com.limoz.fleet.telematics.movement.MovementFlag;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/** Fleet-wide totals of one operational day. */
public record MovementSummaryResponse(
        LocalDate date,
        int vehiclesAnalysed,
        int vehiclesMoved,
        int vehiclesNotMoved,
        int vehiclesWithGpsIssue,
        BigDecimal totalDistanceKm,
        long totalDrivingMinutes,
        long totalIdleMinutes,
        int totalTrips,
        Map<MovementFlag, Long> flaggedCounts) {}
