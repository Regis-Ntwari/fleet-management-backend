package com.limoz.fleet.maintenance.inventory;

/** Derived stock position of a spare part: OUT when nothing is left, LOW at or below the minimum, else OK. */
public enum StockStatus {
    OK, LOW, OUT;

    public static StockStatus of(int currentStock, int minimumStock) {
        if (currentStock <= 0) return OUT;
        return currentStock <= minimumStock ? LOW : OK;
    }

    /** True when moving from {@code from} to {@code to} means the part got scarcer (OK -> LOW, OK -> OUT, LOW -> OUT). */
    public static boolean worsened(StockStatus from, StockStatus to) {
        return to.ordinal() > from.ordinal();
    }
}
