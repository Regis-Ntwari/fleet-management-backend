package com.limoz.fleet.maintenance.domain;

/** Colour band of the "days in garage" indicator; thresholds come from system settings. */
public enum GarageDayBand {
    NORMAL, AMBER, RED;

    public static GarageDayBand of(long days, int amberFrom, int redFrom) {
        if (days >= redFrom) return RED;
        return days >= amberFrom ? AMBER : NORMAL;
    }
}
