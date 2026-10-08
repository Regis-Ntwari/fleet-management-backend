package com.limoz.fleet.finance.dto;

import com.limoz.fleet.finance.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/** Ledger KPIs in RWF: received, paid out, net and the split by method. */
public record PaymentSummaryResponse(LocalDate from, LocalDate to, String currency, long count, BigDecimal received, BigDecimal paidOut,
                                     BigDecimal net, Map<PaymentMethod, BigDecimal> receivedByMethod, Map<PaymentMethod, BigDecimal> paidOutByMethod) {}
