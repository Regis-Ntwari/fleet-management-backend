package com.limoz.fleet.trip.domain;

public enum TripStatus {
    PLANNED, DISPATCHED, IN_PROGRESS, COMPLETED, CANCELLED;

    /** Statuses in which the trip occupies its vehicle and driver for the scheduled window. */
    public boolean isActive() {
        return this == PLANNED || this == DISPATCHED || this == IN_PROGRESS;
    }

    public boolean canTransitionTo(TripStatus to) {
        return switch (this) {
            case PLANNED -> to == DISPATCHED || to == IN_PROGRESS || to == CANCELLED;
            case DISPATCHED -> to == IN_PROGRESS || to == CANCELLED;
            case IN_PROGRESS -> to == COMPLETED || to == CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
    }
}
