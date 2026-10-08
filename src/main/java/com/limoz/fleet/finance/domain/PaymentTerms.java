package com.limoz.fleet.finance.domain;

/** Payment terms; the number of days is added to the issue date to obtain the due date. */
public enum PaymentTerms {
    DUE_ON_RECEIPT(0), NET_7(7), NET_14(14), NET_30(30), NET_45(45), NET_60(60);

    private final int days;

    PaymentTerms(int days) {
        this.days = days;
    }

    public int days() {
        return days;
    }

    /** Closest term for a configured number of days (e.g. the {@code finance.invoice_due_days} setting). */
    public static PaymentTerms forDays(int days) {
        PaymentTerms best = NET_30;
        int distance = Integer.MAX_VALUE;
        for (PaymentTerms term : values()) {
            int d = Math.abs(term.days - days);
            if (d < distance) {
                distance = d;
                best = term;
            }
        }
        return best;
    }
}
