package com.limoz.fleet.maintenance;

import java.math.BigDecimal;

public enum PaymentStatus {
    UNPAID, PARTIAL, PAID;

    public static PaymentStatus of(BigDecimal amountPaid, BigDecimal totalCost) {
        if (amountPaid == null || amountPaid.signum() <= 0) return UNPAID;
        return amountPaid.compareTo(totalCost) >= 0 ? PAID : PARTIAL;
    }
}
