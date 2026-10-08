package com.limoz.fleet.incident;

import com.limoz.fleet.common.persistence.BaseEntity;
import com.limoz.fleet.driver.Driver;
import com.limoz.fleet.vehicle.Vehicle;
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
import java.time.LocalDate;

/** Penalty issued against a fleet vehicle / driver (FN-2026-0012). Settled through the payment ledger. */
@Entity
@Table(name = "traffic_fines")
@Getter
@Setter
@NoArgsConstructor
public class TrafficFine extends BaseEntity {

    @Column(name = "fine_number", nullable = false, unique = true, length = 20)
    private String fineNumber;

    @Column(name = "ticket_reference", length = 60)
    private String ticketReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    @Column(name = "trip_id")
    private Long tripId;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(length = 255)
    private String location;

    @Column(nullable = false, length = 255)
    private String offence;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "RWF";

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FineStatus status = FineStatus.UNPAID;

    @Column(name = "paid_at")
    private Instant paidAt;

    /** Ledger payment (finance module) that settled the fine. */
    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "charged_to_driver", nullable = false)
    private boolean chargedToDriver;

    @Column(name = "attachment_id")
    private Long attachmentId;

    @Column(length = 500)
    private String notes;
}
