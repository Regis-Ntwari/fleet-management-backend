package com.limoz.fleet.customer;

import com.limoz.fleet.common.persistence.BaseEntity;
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
import java.time.LocalDate;

/** Local purchase order (LPO-2026-0188) raised by a client to authorise work and back invoices. */
@Entity
@Table(name = "purchase_orders")
@Getter
@Setter
@NoArgsConstructor
public class PurchaseOrder extends BaseEntity {

    @Column(name = "lpo_number", nullable = false, unique = true, length = 30)
    private String lpoNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commitment_id")
    private Commitment commitment;

    /** Booking id (the Booking aggregate belongs to the dispatch module; only the key is kept here). */
    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "issued_date", nullable = false)
    private LocalDate issuedDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "received_date")
    private LocalDate receivedDate;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal value = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String currency = "RWF";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PurchaseOrderStatus status = PurchaseOrderStatus.OPEN;

    @Column(name = "attachment_id")
    private Long attachmentId;

    @Column(length = 500)
    private String notes;
}
