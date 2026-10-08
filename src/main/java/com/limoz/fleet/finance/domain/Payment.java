package com.limoz.fleet.finance.domain;

import com.limoz.fleet.common.persistence.BaseEntity;
import com.limoz.fleet.customer.domain.Customer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Ledger entry: money received from a client (IN) or paid out (OUT). Entries are never deleted; a
 * mistaken entry is reversed, which keeps the trail and recomputes whatever it settled.
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment extends BaseEntity {

    @Column(name = "payment_number", nullable = false, unique = true, length = 20)
    private String paymentNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private PaymentDirection direction;

    @Column(name = "counterparty_name", nullable = false, length = 150)
    private String counterpartyName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    /** Maintenance record id (the maintenance aggregate belongs to the workshop module). */
    @Column(name = "maintenance_record_id")
    private Long maintenanceRecordId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_id")
    private Expense expense;

    /** Traffic fine id (incident module) settled by this payment. */
    @Column(name = "traffic_fine_id")
    private Long trafficFineId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "RWF";

    @Column(name = "paid_at", nullable = false)
    private Instant paidAt;

    @Column(name = "external_reference", length = 80)
    private String externalReference;

    @Column(name = "receipt_attachment_id")
    private Long receiptAttachmentId;

    @Column(name = "recorded_by_user_id")
    private Long recordedByUserId;

    @Column(length = 500)
    private String notes;

    @Column(nullable = false)
    private boolean reversed;

    @Column(name = "reversal_reason", length = 255)
    private String reversalReason;
}
