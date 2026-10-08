package com.limoz.fleet.trip.domain;

import com.limoz.fleet.booking.domain.Booking;
import com.limoz.fleet.common.persistence.BaseEntity;
import com.limoz.fleet.customer.domain.Customer;
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

/** A vehicle movement with a driver: planned ad hoc or created automatically when a booking slot departs. */
@Entity
@Table(name = "trips")
@Getter
@Setter
@NoArgsConstructor
public class Trip extends BaseEntity {

    @Column(name = "trip_number", nullable = false, unique = true, length = 20)
    private String tripNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @Column(name = "booking_slot_id")
    private Long bookingSlotId;

    @Column(nullable = false, length = 255)
    private String origin;

    @Column(nullable = false, length = 255)
    private String destination;

    @Column(name = "route_description", length = 500)
    private String routeDescription;

    @Column(length = 255)
    private String purpose;

    @Column(name = "passenger_details", length = 500)
    private String passengerDetails;

    private Integer passengers;

    @Column(name = "scheduled_start_at", nullable = false)
    private Instant scheduledStartAt;

    @Column(name = "scheduled_end_at")
    private Instant scheduledEndAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "start_odometer_km")
    private Long startOdometerKm;

    @Column(name = "end_odometer_km")
    private Long endOdometerKm;

    @Column(name = "distance_km", precision = 10, scale = 1)
    private BigDecimal distanceKm;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "fuel_used_litres", precision = 10, scale = 2)
    private BigDecimal fuelUsedLitres;

    @Column(name = "max_speed_kph", precision = 6, scale = 1)
    private BigDecimal maxSpeedKph;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TripStatus status = TripStatus.PLANNED;

    @Column(name = "cancellation_reason", length = 255)
    private String cancellationReason;

    @Column(columnDefinition = "text")
    private String notes;

    /** Effective end of the scheduled window (open-ended trips end when they start). */
    public Instant scheduledWindowEnd() {
        return scheduledEndAt != null ? scheduledEndAt : scheduledStartAt;
    }
}
