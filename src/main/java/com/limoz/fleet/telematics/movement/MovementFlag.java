package com.limoz.fleet.telematics.movement;

/** Conditions detected on a vehicle's daily movement; stored as a JSON array of names in {@code flags}. */
public enum MovementFlag {
    /** No movement at all during the day. */
    NOT_MOVED,
    /** Driving time above {@code movement.excessive_driving_hours}. */
    EXCESSIVE_DRIVING_HOURS,
    /** Distance above {@code movement.high_daily_distance_km}. */
    HIGH_DAILY_DISTANCE,
    /** Driving inside the {@code movement.night_start} - {@code movement.night_end} window. */
    NIGHT_DRIVING,
    /** Maximum speed above {@code movement.speed_limit_kph}. */
    OVER_SPEEDING,
    /** The vehicle has an active GPS device but produced no position sample. */
    GPS_OFFLINE
}
