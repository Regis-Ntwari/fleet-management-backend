package com.limoz.fleet.maintenance.domain;

/** Maintenance-specific runtime settings introduced by migration V82 (the shared keys live in {@code SettingKeys}). */
public final class MaintenanceSettingKeys {

    private MaintenanceSettingKeys() {}

    /** Days in garage from which a job is shown in amber on the garage dashboard. */
    public static final String GARAGE_AMBER_DAYS = "maintenance.garage_amber_days";

    /** Days in garage from which a job is shown in red on the garage dashboard. */
    public static final String GARAGE_RED_DAYS = "maintenance.garage_red_days";
}
