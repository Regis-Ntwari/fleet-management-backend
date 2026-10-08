package com.limoz.fleet.booking;

public enum SlotStatus {
    UNASSIGNED, ASSIGNED, DEPLOYED, RETURNED, CANCELLED;

    /** Slots that hold (reserve) a vehicle and driver for their date range. */
    public boolean isHolding() {
        return this == ASSIGNED || this == DEPLOYED;
    }
}
