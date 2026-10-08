package com.limoz.fleet.customer;

public enum CommitmentStatus {
    DRAFT, ACTIVE, EXPIRING_SOON, CLOSED, CANCELLED;

    /** A commitment bookings and LPOs may still draw down against. */
    public boolean isOpen() {
        return this == ACTIVE || this == EXPIRING_SOON;
    }

    public boolean isFinal() {
        return this == CLOSED || this == CANCELLED;
    }
}
