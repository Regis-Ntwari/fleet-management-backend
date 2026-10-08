package com.limoz.fleet.notification.alert;

/** Lifecycle of an alert (CHECK constraint on alerts.status). */
public enum AlertStatus {
    ACTIVE, ACKNOWLEDGED, RESOLVED;

    public boolean isOpen() {
        return this != RESOLVED;
    }
}
