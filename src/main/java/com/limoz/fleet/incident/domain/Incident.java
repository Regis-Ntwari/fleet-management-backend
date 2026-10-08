package com.limoz.fleet.incident.domain;

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

/** Accident, breakdown, theft or other operational incident involving a fleet vehicle (INC-2026-0007). */
@Entity
@Table(name = "incidents")
@Getter
@Setter
@NoArgsConstructor
public class Incident extends BaseEntity {

    @Column(name = "incident_number", nullable = false, unique = true, length = 20)
    private String incidentNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    /** Trip id (trips belong to the dispatch module). */
    @Column(name = "trip_id")
    private Long tripId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(length = 255)
    private String location;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "incident_type", nullable = false, length = 25)
    private IncidentType incidentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private IncidentSeverity severity = IncidentSeverity.MINOR;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "third_party_involved", nullable = false)
    private boolean thirdPartyInvolved;

    @Column(nullable = false)
    private boolean injuries;

    @Column(name = "police_report_number", length = 60)
    private String policeReportNumber;

    @Column(name = "fuel_loss_litres", precision = 10, scale = 2)
    private BigDecimal fuelLossLitres;

    @Column(name = "estimated_cost", precision = 14, scale = 2)
    private BigDecimal estimatedCost;

    @Column(name = "investigation_notes", columnDefinition = "text")
    private String investigationNotes;

    @Column(name = "corrective_action", columnDefinition = "text")
    private String correctiveAction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncidentStatus status = IncidentStatus.OPEN;

    @Column(name = "reported_by_user_id")
    private Long reportedByUserId;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "closed_at")
    private Instant closedAt;
}
