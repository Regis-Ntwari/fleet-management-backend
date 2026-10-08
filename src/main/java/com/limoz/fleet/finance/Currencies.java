package com.limoz.fleet.finance;

import com.limoz.fleet.common.exception.BusinessRuleException;

import java.util.Set;

/** Currencies accepted on commercial documents (contracts, LPOs, invoices, payments, expenses). */
public final class Currencies {

    public static final String RWF = "RWF";
    public static final String USD = "USD";
    public static final Set<String> SUPPORTED = Set.of(RWF, USD);

    private Currencies() {}

    /** Normalises a currency code (null/blank = RWF) and rejects anything other than RWF or USD. */
    public static String normalise(String currency) {
        if (currency == null || currency.isBlank()) {
            return RWF;
        }
        String code = currency.trim().toUpperCase();
        if (!SUPPORTED.contains(code)) {
            throw new BusinessRuleException("UNSUPPORTED_CURRENCY", "Currency " + currency + " is not supported (RWF or USD)");
        }
        return code;
    }
}
