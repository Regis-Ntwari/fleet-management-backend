package com.limoz.fleet.assignment;

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

import java.time.Instant;

/** Historical record of which driver was responsible for which vehicle. Rows are never deleted. */
@Entity
@Table(name = "vehicle_assignments")
@Getter
@Setter
@NoArgsConstructor
public class VehicleAssignment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at")
    private Instant endAt;

    @Column(name = "assigned_by_user_id")
    private Long assignedByUserId;

    @Column(name = "ended_by_user_id")
    private Long endedByUserId;

    @Column(length = 255)
    private String purpose;

    @Column(name = "odometer_at_assignment")
    private Long odometerAtAssignment;

    @Column(name = "odometer_at_return")
    private Long odometerAtReturn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssignmentStatus status = AssignmentStatus.ACTIVE;

    @Column(length = 500)
    private String comments;
}
