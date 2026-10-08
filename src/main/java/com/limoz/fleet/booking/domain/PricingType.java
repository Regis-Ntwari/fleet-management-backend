package com.limoz.fleet.booking.domain;

public enum PricingType {
    FULL_DAY, HALF_DAY, PER_TRIP, MONTHLY, PER_KM;

    /** Daily pricing types use the booking line's unit price as the voucher day rate. */
    public boolean isDaily() {
        return this == FULL_DAY || this == HALF_DAY;
    }
}
