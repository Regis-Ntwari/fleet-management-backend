package com.limoz.fleet.customer.domain;

public enum PurchaseOrderStatus {
    OPEN, PART_INVOICED, INVOICED, EXPIRED, CLOSED, CANCELLED;

    /** An LPO that can still back a new invoice. */
    public boolean isInvoiceable() {
        return this == OPEN || this == PART_INVOICED;
    }

    public boolean isFinal() {
        return this == CLOSED || this == CANCELLED;
    }
}
