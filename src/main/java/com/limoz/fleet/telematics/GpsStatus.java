package com.limoz.fleet.telematics;

/** GPS communication health of a telematics device (matches the CHECK constraint on telematics_devices). */
public enum GpsStatus {
    ONLINE, OFFLINE, NO_SIGNAL, DISCONNECTED, UNKNOWN;

    /** Statuses that count as a GPS problem on the devices "problems" view and in the alert centre. */
    public boolean isProblem() {
        return this == OFFLINE || this == NO_SIGNAL || this == DISCONNECTED;
    }
}
