package com.limoz.fleet.finance;

public enum InvoiceStatus {
    DRAFT, ISSUED, PARTIALLY_PAID, PAID, OVERDUE, CANCELLED;

    /** Issued invoices that still carry a balance a client can pay. */
    public boolean isPayable() {
        return this == ISSUED || this == PARTIALLY_PAID || this == OVERDUE;
    }

    /** Counts towards billed / outstanding figures. */
    public boolean isCommercial() {
        return this != DRAFT && this != CANCELLED;
    }
}
