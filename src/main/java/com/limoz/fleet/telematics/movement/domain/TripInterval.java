package com.limoz.fleet.telematics.movement.domain;

import java.math.BigDecimal;
import java.time.Instant;

/** A completed or running trip of one vehicle, read from the trips table for movement analysis. */
public record TripInterval(Long tripId, Instant startedAt, Instant endedAt, BigDecimal distanceKm, Integer durationMinutes,
                           BigDecimal maxSpeedKph) {}
