package com.limoz.fleet.booking.domain;

import com.limoz.fleet.common.persistence.BaseEntity;
import com.limoz.fleet.driver.domain.Driver;
import com.limoz.fleet.vehicle.domain.Vehicle;
import com.limoz.fleet.vehicle.domain.VehicleCategory;
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
import java.time.LocalDate;

/** One deployable vehicle unit of a booking line; the dispatcher assigns a vehicle and driver to it. */
@Entity
@Table(name = "booking_slots")
@Getter
@Setter
@NoArgsConstructor
public class BookingSlot extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_line_id", nullable = false)
    private BookingLine line;

    @Column(name = "slot_number", nullable = false)
    private int slotNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private VehicleCategory category;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Shift shift = Shift.DAY;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SlotStatus status = SlotStatus.UNASSIGNED;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "assigned_by_user_id")
    private Long assignedByUserId;

    @Column(name = "departed_at")
    private Instant departedAt;

    @Column(name = "returned_at")
    private Instant returnedAt;

    @Column(name = "odometer_out")
    private Long odometerOut;

    @Column(name = "odometer_in")
    private Long odometerIn;

    /** Trip created at departure (the trip aggregate references the slot back by id). */
    @Column(name = "trip_id")
    private Long tripId;

    @Column(length = 255)
    private String notes;

    public boolean overlaps(LocalDate from, LocalDate to) {
        return !startDate.isAfter(to) && !endDate.isBefore(from);
    }
}
