package com.limoz.fleet.vehicle;

import com.limoz.fleet.common.persistence.BaseEntity;
import com.limoz.fleet.driver.Driver;
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

@Entity
@Table(name = "vehicles")
@Getter
@Setter
@NoArgsConstructor
public class Vehicle extends BaseEntity {

    @Column(name = "plate_number", nullable = false, length = 20)
    private String plateNumber;

    @Column(name = "fleet_number", length = 20)
    private String fleetNumber;

    @Column(nullable = false, length = 60)
    private String make;

    @Column(nullable = false, length = 80)
    private String model;

    @Column(name = "model_year")
    private Integer modelYear;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private VehicleCategory category;

    @Column(name = "body_type", length = 40)
    private String bodyType;

    @Enumerated(EnumType.STRING)
    @Column(name = "fuel_type", nullable = false, length = 20)
    private FuelType fuelType = FuelType.DIESEL;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Transmission transmission;

    @Column(name = "engine_number", length = 60)
    private String engineNumber;

    @Column(name = "chassis_number", length = 60)
    private String chassisNumber;

    @Column(length = 40)
    private String color;

    @Column(name = "odometer_km", nullable = false)
    private long odometerKm;

    @Column(name = "seating_capacity")
    private Integer seatingCapacity;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Column(name = "acquisition_cost", precision = 16, scale = 2)
    private BigDecimal acquisitionCost;

    @Enumerated(EnumType.STRING)
    @Column(name = "ownership_type", nullable = false, length = 20)
    private OwnershipType ownershipType = OwnershipType.OWNED;

    @Column(name = "owner_name", length = 150)
    private String ownerName;

    @Column(name = "owner_contact", length = 60)
    private String ownerContact;

    @Column(name = "owner_driver_name", length = 120)
    private String ownerDriverName;

    @Column(name = "insurance_provider", length = 120)
    private String insuranceProvider;

    @Column(name = "insurance_policy_number", length = 80)
    private String insurancePolicyNumber;

    @Column(name = "insurance_expiry_date")
    private LocalDate insuranceExpiryDate;

    @Column(name = "day_rate", precision = 14, scale = 2)
    private BigDecimal dayRate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_driver_id")
    private Driver currentDriver;

    @Enumerated(EnumType.STRING)
    @Column(name = "operational_status", nullable = false, length = 20)
    private VehicleStatus operationalStatus = VehicleStatus.AVAILABLE;

    @Enumerated(EnumType.STRING)
    @Column(name = "maintenance_status", nullable = false, length = 20)
    private MaintenanceStatus maintenanceStatus = MaintenanceStatus.OK;

    @Column(length = 80)
    private String department;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(nullable = false)
    private boolean archived;

    @Column(name = "archived_at")
    private Instant archivedAt;

    public String getDisplayName() {
        return make + " " + model + " (" + plateNumber + ")";
    }

    /** Canonical plate format: upper case, single spaces. */
    public static String normalisePlate(String plate) {
        return plate == null ? null : plate.trim().toUpperCase().replaceAll("\\s+", " ");
    }
}
