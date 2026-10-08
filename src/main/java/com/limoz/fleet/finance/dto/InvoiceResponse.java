package com.limoz.fleet.finance.dto;

import com.limoz.fleet.customer.dto.CustomerSummary;
import com.limoz.fleet.customer.dto.PurchaseOrderSummary;
import com.limoz.fleet.finance.domain.InvoiceStatus;
import com.limoz.fleet.finance.domain.PaymentTerms;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record InvoiceResponse(
        Long id,
        String invoiceNumber,
        CustomerSummary customer,
        Long bookingId,
        PurchaseOrderSummary purchaseOrder,
        LocalDate issueDate,
        LocalDate dueDate,
        PaymentTerms paymentTerms,
        String currency,
        BigDecimal subtotal,
        BigDecimal discountPercent,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        BigDecimal amountPaid,
        BigDecimal balanceDue,
        InvoiceStatus status,
        boolean overdue,
        Instant sentAt,
        Instant paidAt,
        String notes,
        List<InvoiceLineResponse> lines,
        List<PaymentResponse> payments,
        Instant createdAt,
        Instant updatedAt,
        String createdBy) {}
