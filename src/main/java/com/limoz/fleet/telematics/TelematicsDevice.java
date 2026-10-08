package com.limoz.fleet.telematics;

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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** GPS tracker installed in a vehicle (at most one per vehicle). */
@Entity
@Table(name = "telematics_devices")
@Getter
@Setter
@NoArgsConstructor
public class TelematicsDevice extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false, unique = true)
    private Vehicle vehicle;

    @Column(name = "provider_code", nullable = false, length = 30)
    private String providerCode = "manual";

    @Column(name = "external_device_id", length = 80)
    private String externalDeviceId;

    @Column(name = "sim_number", length = 30)
    private String simNumber;

    @Column(name = "installed_at")
    private LocalDate installedAt;

    @Column(nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "gps_status", nullable = false, length = 20)
    private GpsStatus gpsStatus = GpsStatus.UNKNOWN;

    @Enumerated(EnumType.STRING)
    @Column(name = "fuel_sensor_status", nullable = false, length = 20)
    private FuelSensorStatus fuelSensorStatus = FuelSensorStatus.NOT_INSTALLED;

    @Column(name = "last_communication_at")
    private Instant lastCommunicationAt;

    @Column(name = "last_latitude", precision = 9, scale = 6)
    private BigDecimal lastLatitude;

    @Column(name = "last_longitude", precision = 9, scale = 6)
    private BigDecimal lastLongitude;

    @Column(name = "last_speed_kph", precision = 6, scale = 1)
    private BigDecimal lastSpeedKph;

    @Column(name = "last_odometer_km")
    private Long lastOdometerKm;

    @Column(name = "last_ignition_on")
    private Boolean lastIgnitionOn;

    @Column(name = "last_battery_voltage", precision = 5, scale = 2)
    private BigDecimal lastBatteryVoltage;

    @Column(length = 255)
    private String notes;
}
