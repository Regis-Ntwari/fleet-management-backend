package com.limoz.fleet.settings;

/**
 * Keys of runtime-configurable settings. Defaults live in the V2 migration.
 */
public final class SettingKeys {

    private SettingKeys() {}

    // Company profile
    public static final String COMPANY_NAME = "company.name";
    public static final String COMPANY_LEGAL_NAME = "company.legal_name";
    public static final String COMPANY_TIN = "company.tin";
    public static final String COMPANY_EMAIL = "company.email";
    public static final String COMPANY_PHONE = "company.phone";
    public static final String COMPANY_ADDRESS = "company.address";
    public static final String COMPANY_CITY = "company.city";
    public static final String COMPANY_COUNTRY = "company.country";
    public static final String COMPANY_CURRENCY = "company.currency";
    public static final String COMPANY_TIMEZONE = "company.timezone";

    // Movement analysis
    public static final String NIGHT_DRIVING_START = "movement.night_start";
    public static final String NIGHT_DRIVING_END = "movement.night_end";
    public static final String EXCESSIVE_DRIVING_HOURS = "movement.excessive_driving_hours";
    public static final String HIGH_DAILY_DISTANCE_KM = "movement.high_daily_distance_km";
    public static final String IDLE_DAYS_THRESHOLD = "movement.idle_days_threshold";
    public static final String GPS_OFFLINE_MINUTES = "telematics.gps_offline_minutes";
    public static final String SPEED_LIMIT_KPH = "movement.speed_limit_kph";

    // Fuel
    public static final String FUEL_VARIANCE_TOLERANCE_LITRES = "fuel.variance_tolerance_litres";
    public static final String FUEL_HIGH_CONSUMPTION_L_PER_100KM = "fuel.high_consumption_l_per_100km";
    public static final String FUEL_DEFAULT_PRICE_PER_LITRE = "fuel.default_price_per_litre";

    // Documents & maintenance
    public static final String DOCUMENT_EXPIRY_WARNING_DAYS = "documents.expiry_warning_days";
    public static final String LICENSE_EXPIRY_WARNING_DAYS = "documents.license_expiry_warning_days";
    public static final String MAINTENANCE_DUE_SOON_KM = "maintenance.due_soon_km";
    public static final String MAINTENANCE_DUE_SOON_DAYS = "maintenance.due_soon_days";
    public static final String MAINTENANCE_DEFAULT_INTERVAL_KM = "maintenance.default_interval_km";
    public static final String MAINTENANCE_DEFAULT_INTERVAL_DAYS = "maintenance.default_interval_days";

    // Dispatch / bookings
    public static final String BOOKING_REMINDER_HOURS = "booking.reminder_hours";
    public static final String DISPATCH_REQUIRE_VALID_DOCUMENTS = "dispatch.require_valid_documents";
    public static final String DISPATCH_REQUIRE_VALID_LICENSE = "dispatch.require_valid_license";

    // Finance
    public static final String INVOICE_TAX_RATE_PERCENT = "finance.tax_rate_percent";
    public static final String INVOICE_DUE_DAYS = "finance.invoice_due_days";
    public static final String USD_TO_RWF_RATE = "finance.usd_to_rwf_rate";

    // Security
    public static final String SECURITY_MAX_FAILED_LOGINS = "security.max_failed_logins";
    public static final String SECURITY_LOCKOUT_MINUTES = "security.lockout_minutes";

    // Utilisation
    public static final String UTILISATION_HIGH_PERCENT = "utilisation.high_percent";
    public static final String UTILISATION_LOW_PERCENT = "utilisation.low_percent";
}
