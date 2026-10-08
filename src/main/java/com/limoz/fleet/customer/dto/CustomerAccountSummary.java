package com.limoz.fleet.customer.dto;

import java.math.BigDecimal;

/**
 * Account position of a client as shown on the client profile. Monetary figures are consolidated in RWF
 * (USD documents converted at the configured {@code finance.usd_to_rwf_rate}).
 */
public record CustomerAccountSummary(
        Long customerId,
        String customerName,
        long activeBookings,
        long commitments,
        long openPurchaseOrders,
        long invoices,
        BigDecimal totalBilled,
        BigDecimal totalPaid,
        BigDecimal outstandingBalance,
        long overdueInvoices,
        String currency) {}
