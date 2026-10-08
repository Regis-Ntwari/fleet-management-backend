package com.limoz.fleet.document;

import java.time.LocalDate;

/** Pure function: document status from expiry date, today and the warning window. */
public final class DocumentStatusCalculator {

    private DocumentStatusCalculator() {}

    public static DocumentStatus compute(LocalDate expiryDate, LocalDate today, int warningDays) {
        if (expiryDate == null) {
            return DocumentStatus.NOT_APPLICABLE;
        }
        if (expiryDate.isBefore(today)) {
            return DocumentStatus.EXPIRED;
        }
        if (!expiryDate.isAfter(today.plusDays(warningDays))) {
            return DocumentStatus.EXPIRING_SOON;
        }
        return DocumentStatus.VALID;
    }
}
