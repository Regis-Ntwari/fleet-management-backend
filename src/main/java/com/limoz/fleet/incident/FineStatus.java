package com.limoz.fleet.incident;

public enum FineStatus {
    UNPAID, PAID, DISPUTED, WAIVED;

    /** Fines that still represent money owed. */
    public boolean isOutstanding() {
        return this == UNPAID || this == DISPUTED;
    }

    public boolean canTransitionTo(FineStatus to) {
        return switch (this) {
            case UNPAID -> to == PAID || to == DISPUTED || to == WAIVED;
            case DISPUTED -> to == PAID || to == WAIVED || to == UNPAID;
            case PAID, WAIVED -> false;
        };
    }
}
