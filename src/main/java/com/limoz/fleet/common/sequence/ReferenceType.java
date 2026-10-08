package com.limoz.fleet.common.sequence;

/**
 * Business reference number formats used across the system. Formats were taken from the reference
 * application (BK-2026-0012, CMT-0042, LPO-2026-0188, MNT-2026-0046, PAY-3301, LIMOZ/000628/2026,
 * GRG/000114/2026) and extended for the remaining modules.
 */
public enum ReferenceType {
    BOOKING("BK", true, 4),
    TRIP("TRP", true, 5),
    MAINTENANCE("MNT", true, 4),
    GARAGE_INTAKE("GRG", true, 6),
    DEPLOYMENT_VOUCHER("LIMOZ", true, 6),
    INCIDENT("INC", true, 4),
    TRAFFIC_FINE("FN", true, 4),
    INVOICE("INV", true, 4),
    PAYMENT("PAY", false, 4),
    EXPENSE("EXP", true, 4),
    COMMITMENT("CMT", false, 4),
    PURCHASE_ORDER("LPO", true, 4),
    STOCK_PURCHASE("PO", false, 4),
    DRIVER("DRV", false, 4),
    FLEET_NUMBER("LZ", false, 3),
    IMPORT_BATCH("IMP", true, 4);

    private final String prefix;
    private final boolean yearly;
    private final int width;

    ReferenceType(String prefix, boolean yearly, int width) {
        this.prefix = prefix;
        this.yearly = yearly;
        this.width = width;
    }

    public String prefix() {
        return prefix;
    }

    public boolean yearly() {
        return yearly;
    }

    public int width() {
        return width;
    }

    public String sequenceKey(int year) {
        return yearly ? prefix + "-" + year : prefix;
    }

    public String format(int year, long value) {
        String number = String.format("%0" + width + "d", value);
        return switch (this) {
            case DEPLOYMENT_VOUCHER, GARAGE_INTAKE -> prefix + "/" + number + "/" + year;
            default -> yearly ? prefix + "-" + year + "-" + number : prefix + "-" + number;
        };
    }
}
