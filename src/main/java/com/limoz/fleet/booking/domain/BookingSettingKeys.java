package com.limoz.fleet.booking.domain;

/** Keys of runtime settings owned by the booking / dispatch module (defaults in migration V81). */
public final class BookingSettingKeys {

    private BookingSettingKeys() {}

    /** Number of days ahead the dispatcher board lists upcoming jobs. */
    public static final String DISPATCH_BOARD_HORIZON_DAYS = "dispatch.board_horizon_days";
}
