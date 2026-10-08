package com.limoz.fleet.maintenance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** Checklist line of a job; a DONE task linked to a service type updates the vehicle's preventive schedule. */
@Entity
@Table(name = "maintenance_tasks")
@Getter
@Setter
@NoArgsConstructor
public class MaintenanceTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "maintenance_record_id", nullable = false)
    private MaintenanceRecord record;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_type_id")
    private ServiceType serviceType;

    @Column(nullable = false, length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TaskStatus status = TaskStatus.PENDING;

    @Column(name = "labor_hours", precision = 6, scale = 2)
    private BigDecimal laborHours;

    @Column(name = "labor_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal laborCost = BigDecimal.ZERO;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "completed_by", length = 150)
    private String completedBy;

    @Column(length = 255)
    private String notes;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
