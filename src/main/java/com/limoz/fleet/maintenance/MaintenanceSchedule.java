package com.limoz.fleet.maintenance;

import com.limoz.fleet.common.persistence.BaseEntity;
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

import java.time.LocalDate;

/** Preventive maintenance schedule of one service type on one vehicle (unique per pair). */
@Entity
@Table(name = "maintenance_schedules")
@Getter
@Setter
@NoArgsConstructor
public class MaintenanceSchedule extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_type_id", nullable = false)
    private ServiceType serviceType;

    @Column(name = "interval_km")
    private Integer intervalKm;

    @Column(name = "interval_days")
    private Integer intervalDays;

    @Column(name = "last_service_odometer")
    private Long lastServiceOdometer;

    @Column(name = "last_service_date")
    private LocalDate lastServiceDate;

    @Column(name = "last_maintenance_record_id")
    private Long lastMaintenanceRecordId;

    @Column(name = "next_service_odometer")
    private Long nextServiceOdometer;

    @Column(name = "next_service_date")
    private LocalDate nextServiceDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ScheduleStatus status = ScheduleStatus.OK;

    @Column(nullable = false)
    private boolean active = true;

    @Column(length = 255)
    private String notes;
}
