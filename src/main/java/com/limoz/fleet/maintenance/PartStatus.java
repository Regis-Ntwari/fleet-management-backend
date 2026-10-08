package com.limoz.fleet.maintenance;

/** REQUESTED by the mechanic, then APPROVED (no stock link) or ISSUED from stock, or REJECTED by the manager. */
public enum PartStatus {
    REQUESTED, APPROVED, REJECTED, ISSUED;

    /** Parts that count toward the job's parts cost. */
    public boolean isCosted() {
        return this == APPROVED || this == ISSUED;
    }
}
