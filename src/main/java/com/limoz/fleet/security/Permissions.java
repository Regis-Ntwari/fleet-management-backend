package com.limoz.fleet.security;

/**
 * Permission codes. These are the authorities carried in the JWT and checked by
 * {@code @PreAuthorize} on every controller. Keep in sync with the {@code permissions} table (V2 migration).
 */
public final class Permissions {

    private Permissions() {}

    public static final String DASHBOARD_VIEW = "DASHBOARD_VIEW";

    public static final String VEHICLE_READ = "VEHICLE_READ";
    public static final String VEHICLE_CREATE = "VEHICLE_CREATE";
    public static final String VEHICLE_UPDATE = "VEHICLE_UPDATE";
    public static final String VEHICLE_DELETE = "VEHICLE_DELETE";
    public static final String VEHICLE_ODOMETER_CORRECT = "VEHICLE_ODOMETER_CORRECT";

    public static final String DRIVER_READ = "DRIVER_READ";
    public static final String DRIVER_MANAGE = "DRIVER_MANAGE";

    public static final String ASSIGNMENT_READ = "ASSIGNMENT_READ";
    public static final String ASSIGNMENT_MANAGE = "ASSIGNMENT_MANAGE";

    public static final String TRIP_READ = "TRIP_READ";
    public static final String TRIP_MANAGE = "TRIP_MANAGE";

    public static final String BOOKING_READ = "BOOKING_READ";
    public static final String BOOKING_MANAGE = "BOOKING_MANAGE";
    public static final String DISPATCH_MANAGE = "DISPATCH_MANAGE";

    public static final String CUSTOMER_READ = "CUSTOMER_READ";
    public static final String CUSTOMER_MANAGE = "CUSTOMER_MANAGE";

    public static final String FUEL_READ = "FUEL_READ";
    public static final String FUEL_MANAGE = "FUEL_MANAGE";

    public static final String MAINTENANCE_READ = "MAINTENANCE_READ";
    public static final String MAINTENANCE_MANAGE = "MAINTENANCE_MANAGE";
    public static final String MAINTENANCE_APPROVE = "MAINTENANCE_APPROVE";
    public static final String INVENTORY_READ = "INVENTORY_READ";
    public static final String INVENTORY_MANAGE = "INVENTORY_MANAGE";

    public static final String INCIDENT_READ = "INCIDENT_READ";
    public static final String INCIDENT_MANAGE = "INCIDENT_MANAGE";
    public static final String FINE_MANAGE = "FINE_MANAGE";

    public static final String DOCUMENT_READ = "DOCUMENT_READ";
    public static final String DOCUMENT_MANAGE = "DOCUMENT_MANAGE";

    public static final String FINANCE_READ = "FINANCE_READ";
    public static final String FINANCE_MANAGE = "FINANCE_MANAGE";
    public static final String EXPENSE_APPROVE = "EXPENSE_APPROVE";

    public static final String TELEMATICS_READ = "TELEMATICS_READ";
    public static final String TELEMATICS_MANAGE = "TELEMATICS_MANAGE";

    public static final String REPORT_VIEW = "REPORT_VIEW";
    public static final String REPORT_EXPORT = "REPORT_EXPORT";

    public static final String NOTIFICATION_READ = "NOTIFICATION_READ";
    public static final String ALERT_MANAGE = "ALERT_MANAGE";

    public static final String AUDIT_VIEW = "AUDIT_VIEW";
    public static final String USER_MANAGE = "USER_MANAGE";
    public static final String ROLE_MANAGE = "ROLE_MANAGE";
    public static final String SETTINGS_MANAGE = "SETTINGS_MANAGE";
    public static final String IMPORT_DATA = "IMPORT_DATA";
}
