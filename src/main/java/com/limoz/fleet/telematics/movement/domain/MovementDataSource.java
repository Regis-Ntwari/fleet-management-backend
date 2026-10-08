package com.limoz.fleet.telematics.movement.domain;

/** Where a daily summary's figures come from (CHECK constraint {@code data_source} on daily_movement_summaries). */
public enum MovementDataSource {
    /** GPS positions only. */
    TELEMATICS,
    /** Trip records only (no positions for the day). */
    TRIPS,
    /** GPS positions for the movement figures, trip records for the trip count. */
    MIXED,
    /** Neither positions nor trips: the vehicle is assumed not to have moved. */
    NONE
}
