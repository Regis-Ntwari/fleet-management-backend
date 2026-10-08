package com.limoz.fleet.customer.dto;

import com.limoz.fleet.customer.PurchaseOrderStatus;

import java.math.BigDecimal;

/** Compact LPO reference embedded in invoices and vouchers. */
public record PurchaseOrderSummary(Long id, String lpoNumber, Long customerId, BigDecimal value, String currency, PurchaseOrderStatus status) {}
