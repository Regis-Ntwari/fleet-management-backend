package com.limoz.fleet.notification.alert;

/**
 * Catalogue of alert conditions raised by {@link AlertScanner}s. Modules raising a type that is not listed here
 * add it to this enum; the column is a free VARCHAR(40) so no migration is needed.
 */
public enum AlertType {
    GPS_OFFLINE,
    FUEL_SENSOR_FAULT,
    VEHICLE_NOT_MOVED,
    VEHICLE_IDLE_EXCESSIVE,
    EXCESSIVE_DRIVING_HOURS,
    HIGH_DAILY_DISTANCE,
    NIGHT_DRIVING,
    OVER_SPEEDING,
    DOCUMENT_EXPIRING,
    DOCUMENT_EXPIRED,
    LICENSE_EXPIRING,
    LICENSE_EXPIRED,
    MAINTENANCE_DUE,
    MAINTENANCE_OVERDUE,
    VEHICLE_IN_WORKSHOP_LONG,
    LOW_STOCK,
    FUEL_ANOMALY,
    HIGH_FUEL_CONSUMPTION,
    INCIDENT_OPEN,
    ACCIDENT_REPORTED,
    FINE_UNPAID,
    BOOKING_APPROACHING,
    BOOKING_UNASSIGNED,
    INVOICE_OVERDUE,
    COMMITMENT_EXPIRING,
    LPO_EXPIRING,
    VEHICLE_OUT_OF_SERVICE;

    /** GPS-related types additionally notify IT administrators when critical. */
    public boolean isGpsRelated() {
        return this == GPS_OFFLINE || this == FUEL_SENSOR_FAULT;
    }
}
