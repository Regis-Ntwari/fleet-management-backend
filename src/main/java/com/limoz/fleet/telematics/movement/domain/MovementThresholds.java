package com.limoz.fleet.telematics.movement.domain;

import java.time.LocalTime;

/**
 * Runtime thresholds of the movement analysis (settings {@code movement.*}).
 *
 * @param nightStart            start of the night window (local time); may be later than {@code nightEnd} when the window wraps midnight
 * @param nightEnd              end of the night window (local time, exclusive)
 * @param excessiveDrivingHours driving time above this many hours is flagged
 * @param highDailyDistanceKm   distance above this many km is flagged
 * @param speedLimitKph         maximum speed above this is flagged
 */
public record MovementThresholds(LocalTime nightStart, LocalTime nightEnd, int excessiveDrivingHours, int highDailyDistanceKm, int speedLimitKph) {}
