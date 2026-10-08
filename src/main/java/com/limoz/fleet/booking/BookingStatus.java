package com.limoz.fleet.booking;

import java.util.Set;

public enum BookingStatus {
    DRAFT, REQUESTED, CONFIRMED, READY_FOR_DEPLOYMENT, DEPLOYED, READY_FOR_BILLING, COMPLETED, CANCELLED;

    /** Statuses in which the order can still be edited by the booking desk. */
    public boolean isEditable() {
        return this == DRAFT || this == REQUESTED || this == CONFIRMED;
    }

    /** Statuses in which the dispatcher may assign vehicles and drivers to slots. */
    public boolean isDispatchable() {
        return this == CONFIRMED || this == READY_FOR_DEPLOYMENT || this == DEPLOYED;
    }

    public boolean isClosed() {
        return this == COMPLETED || this == CANCELLED;
    }

    public boolean canTransitionTo(BookingStatus to) {
        return switch (this) {
            case DRAFT, REQUESTED -> to == CONFIRMED || to == CANCELLED;
            case CONFIRMED -> Set.of(READY_FOR_DEPLOYMENT, DEPLOYED, CANCELLED).contains(to);
            case READY_FOR_DEPLOYMENT -> Set.of(CONFIRMED, DEPLOYED, CANCELLED).contains(to);
            case DEPLOYED -> to == READY_FOR_BILLING || to == CANCELLED;
            case READY_FOR_BILLING -> to == COMPLETED || to == DEPLOYED || to == CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
    }
}
