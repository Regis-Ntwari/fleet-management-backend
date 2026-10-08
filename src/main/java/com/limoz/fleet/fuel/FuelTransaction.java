package com.limoz.fleet.fuel;

import com.limoz.fleet.common.persistence.BaseEntity;
import com.limoz.fleet.driver.Driver;
import com.limoz.fleet.vehicle.FuelType;
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

/** One refuelling of a vehicle, with consumption derived from the chronologically previous transaction. */
@Entity
@Table(name = "fuel_transactions")
@Getter
@Setter
@NoArgsConstructor
public class FuelTransaction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    @Column(name = "booking_slot_id")
    private Long bookingSlotId;

    @Column(name = "trip_id")
    private Long tripId;

    @Column(name = "transaction_at", nullable = false)
    private Instant transactionAt;

    @Column(name = "station_name", nullable = false, length = 120)
    private String stationName;

    @Column(name = "supplier_name", length = 120)
    private String supplierName;

    @Enumerated(EnumType.STRING)
    @Column(name = "fuel_type", nullable = false, length = 20)
    private FuelType fuelType;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal litres;

    @Column(name = "price_per_litre", nullable = false, precision = 12, scale = 2)
    private BigDecimal pricePerLitre;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3)
    private String currency = "RWF";

    @Column(name = "odometer_km", nullable = false)
    private long odometerKm;

    @Column(name = "previous_odometer_km")
    private Long previousOdometerKm;

    @Column(name = "distance_since_last_km", precision = 10, scale = 1)
    private BigDecimal distanceSinceLastKm;

    @Column(name = "consumption_l_per_100km", precision = 8, scale = 2)
    private BigDecimal consumptionLPer100km;

    @Column(name = "km_per_litre", precision = 8, scale = 2)
    private BigDecimal kmPerLitre;

    @Column(name = "sensor_detected_litres", precision = 10, scale = 2)
    private BigDecimal sensorDetectedLitres;

    @Column(name = "variance_litres", precision = 10, scale = 2)
    private BigDecimal varianceLitres;

    @Column(nullable = false)
    private boolean anomaly;

    @Column(name = "anomaly_reason", length = 255)
    private String anomalyReason;

    @Column(name = "full_tank", nullable = false)
    private boolean fullTank = true;

    @Column(name = "receipt_number", length = 60)
    private String receiptNumber;

    @Column(name = "receipt_attachment_id")
    private Long receiptAttachmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20)
    private FuelPaymentMethod paymentMethod;

    @Column(name = "entered_by_user_id")
    private Long enteredByUserId;

    @Column(length = 500)
    private String notes;

    @Column(nullable = false)
    private boolean archived;
}
