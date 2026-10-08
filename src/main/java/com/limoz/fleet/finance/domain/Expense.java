package com.limoz.fleet.finance.domain;

import com.limoz.fleet.common.persistence.BaseEntity;
import com.limoz.fleet.driver.domain.Driver;
import com.limoz.fleet.vehicle.domain.Vehicle;
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

/** Operational cost outside fuel and maintenance, with an approval workflow (PENDING -> APPROVED -> PAID). */
@Entity
@Table(name = "expenses")
@Getter
@Setter
@NoArgsConstructor
public class Expense extends BaseEntity {

    @Column(name = "expense_number", nullable = false, unique = true, length = 20)
    private String expenseNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private ExpenseCategory category;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "RWF";

    @Column(name = "incurred_on", nullable = false)
    private LocalDate incurredOn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    @Column(name = "trip_id")
    private Long tripId;

    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "submitted_by_user_id")
    private Long submittedByUserId;

    @Column(name = "submitted_by_name", length = 150)
    private String submittedByName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ExpenseStatus status = ExpenseStatus.PENDING;

    @Column(name = "approved_by_user_id")
    private Long approvedByUserId;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "rejection_reason", length = 255)
    private String rejectionReason;

    @Column(name = "receipt_attachment_id")
    private Long receiptAttachmentId;

    @Column(length = 500)
    private String notes;
}
