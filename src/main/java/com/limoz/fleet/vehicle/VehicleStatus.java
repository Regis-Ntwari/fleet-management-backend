package com.limoz.fleet.vehicle;

public enum VehicleStatus {
    AVAILABLE, ASSIGNED, ON_TRIP, RESERVED, IN_MAINTENANCE, OUT_OF_SERVICE, INACTIVE;

    /** Statuses in which a vehicle may be dispatched / assigned. */
    public boolean isDispatchable() {
        return this == AVAILABLE || this == ASSIGNED || this == RESERVED;
    }

    public boolean isOperational() {
        return this != OUT_OF_SERVICE && this != INACTIVE;
    }
}
