package com.limoz.fleet.telematics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One stored GPS fix of a vehicle. Append-only time series (unique per vehicle and timestamp); the table carries
 * no auditing columns, so this entity deliberately does not extend {@code BaseEntity}.
 */
@Entity
@Table(name = "vehicle_positions")
@Getter
@Setter
@NoArgsConstructor
public class VehiclePosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vehicle_id", nullable = false)
    private Long vehicleId;

    @Column(name = "device_id")
    private Long deviceId;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "speed_kph", nullable = false, precision = 6, scale = 1)
    private BigDecimal speedKph = BigDecimal.ZERO;

    @Column(precision = 5, scale = 1)
    private BigDecimal heading;

    @Column(name = "odometer_km", precision = 12, scale = 1)
    private BigDecimal odometerKm;

    @Column(name = "ignition_on")
    private Boolean ignitionOn;

    @Column(name = "battery_voltage", precision = 5, scale = 2)
    private BigDecimal batteryVoltage;

    @Column(name = "fuel_level_litres", precision = 8, scale = 2)
    private BigDecimal fuelLevelLitres;

    @Column(nullable = false, length = 20)
    private String source = "provider";
}
