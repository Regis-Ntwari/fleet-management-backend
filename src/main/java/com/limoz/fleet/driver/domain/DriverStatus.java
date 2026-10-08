package com.limoz.fleet.driver.domain;

public enum DriverStatus {
    AVAILABLE, ASSIGNED, ON_TRIP, OFF_DUTY, ON_LEAVE, SUSPENDED, INACTIVE;

    public boolean canBeAssigned() {
        return this == AVAILABLE || this == ASSIGNED;
    }
}
