package com.limoz.fleet.maintenance.domain;

import com.limoz.fleet.common.persistence.BaseEntity;
import com.limoz.fleet.customer.domain.Customer;
import com.limoz.fleet.driver.domain.Driver;
import com.limoz.fleet.vehicle.domain.Vehicle;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A maintenance job. Covers both the simple MNT job and the garage intake -> mechanic review -> work
 * progress -> gate pass flow (an intake additionally carries a GRG intake number).
 */
@Entity
@Table(name = "maintenance_records")
@Getter
@Setter
@NoArgsConstructor
public class MaintenanceRecord extends BaseEntity {

    @Column(name = "maintenance_number", nullable = false, unique = true, length = 20)
    private String maintenanceNumber;

    @Column(name = "intake_number", unique = true, length = 30)
    private String intakeNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(name = "reported_at", nullable = false)
    private Instant reportedAt;

    @Column(name = "reported_by_user_id")
    private Long reportedByUserId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "owner_name", length = 150)
    private String ownerName;

    @Column(length = 80)
    private String department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    @Column(name = "driver_name", length = 120)
    private String driverName;

    @Column(name = "driver_contact", length = 30)
    private String driverContact;

    @Column(nullable = false, columnDefinition = "text")
    private String complaint;

    @Column(name = "visible_condition", columnDefinition = "text")
    private String visibleCondition;

    @Enumerated(EnumType.STRING)
    @Column(name = "maintenance_type", nullable = false, length = 20)
    private MaintenanceType maintenanceType = MaintenanceType.CORRECTIVE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Priority priority = Priority.MEDIUM;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workshop_id")
    private Workshop workshop;

    @Column(name = "technician_user_id")
    private Long technicianUserId;

    @Column(name = "technician_name", length = 120)
    private String technicianName;

    @Column(name = "manager_user_id")
    private Long managerUserId;

    @Column(name = "incident_id")
    private Long incidentId;

    @Column(name = "odometer_km")
    private Long odometerKm;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "expected_completion_at")
    private LocalDate expectedCompletionAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "gate_pass_number", length = 30)
    private String gatePassNumber;

    @Column(name = "review_date")
    private LocalDate reviewDate;

    @Column(columnDefinition = "text")
    private String diagnosis;

    @Column(name = "observed_faults", columnDefinition = "text")
    private String observedFaults;

    @Column(name = "recommended_repair", columnDefinition = "text")
    private String recommendedRepair;

    @Column(name = "labour_notes", columnDefinition = "text")
    private String labourNotes;

    @Column(name = "service_performed", columnDefinition = "text")
    private String servicePerformed;

    @Column(name = "labor_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal laborCost = BigDecimal.ZERO;

    @Column(name = "parts_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal partsCost = BigDecimal.ZERO;

    @Column(name = "other_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal otherCost = BigDecimal.ZERO;

    @Column(name = "total_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalCost = BigDecimal.ZERO;

    @Column(name = "amount_paid", nullable = false, precision = 14, scale = 2)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 10)
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MaintenanceRecordStatus status = MaintenanceRecordStatus.REPORTED;

    @Column(name = "approved_by_user_id")
    private Long approvedByUserId;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "cancellation_reason", length = 255)
    private String cancellationReason;

    @Column(columnDefinition = "text")
    private String comments;

    @OneToMany(mappedBy = "record", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<MaintenanceTask> tasks = new ArrayList<>();

    @OneToMany(mappedBy = "record", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<MaintenancePart> parts = new ArrayList<>();

    @OneToMany(mappedBy = "record", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC, id ASC")
    private List<MaintenanceComment> commentEntries = new ArrayList<>();

    public boolean isGarageIntake() {
        return intakeNumber != null;
    }
}
