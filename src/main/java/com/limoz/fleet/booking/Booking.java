package com.limoz.fleet.booking;

import com.limoz.fleet.common.persistence.BaseEntity;
import com.limoz.fleet.customer.Customer;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A client order for one or more vehicles over a period. The booking owns its lines (what was requested),
 * its slots (one per requested vehicle, assigned by the dispatcher) and its extra charges.
 */
@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
public class Booking extends BaseEntity {

    @Column(name = "booking_number", nullable = false, unique = true, length = 20)
    private String bookingNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    /** Framework contract the booking draws down against; the commitment aggregate lives in another module. */
    @Column(name = "commitment_id")
    private Long commitmentId;

    @Column(name = "contact_name", length = 120)
    private String contactName;

    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

    @Column(name = "contact_email", length = 150)
    private String contactEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_type", nullable = false, length = 30)
    private ServiceType serviceType = ServiceType.CHARTER;

    @Column(name = "pickup_location", length = 255)
    private String pickupLocation;

    @Column(name = "dropoff_location", length = 255)
    private String dropoffLocation;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "pickup_time")
    private LocalTime pickupTime;

    @Column(name = "return_time")
    private LocalTime returnTime;

    private Integer passengers;

    @Column(nullable = false, length = 3)
    private String currency = "RWF";

    @Column(name = "total_amount", nullable = false, precision = 16, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private BookingStatus status = BookingStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingSource source = BookingSource.INTERNAL;

    @Column(name = "cancellation_reason", length = 255)
    private String cancellationReason;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "deployed_at")
    private Instant deployedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(columnDefinition = "text")
    private String notes;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id asc")
    private List<BookingLine> lines = new ArrayList<>();

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("slotNumber asc")
    private List<BookingSlot> slots = new ArrayList<>();

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id asc")
    private List<BookingExtraCharge> extraCharges = new ArrayList<>();

    public void addLine(BookingLine line) {
        line.setBooking(this);
        lines.add(line);
    }

    public void addSlot(BookingSlot slot) {
        slot.setBooking(this);
        slots.add(slot);
    }

    public void addExtraCharge(BookingExtraCharge charge) {
        charge.setBooking(this);
        extraCharges.add(charge);
    }

    /** Slots that still count towards the deployment (everything except cancelled ones). */
    public List<BookingSlot> activeSlots() {
        return slots.stream().filter(s -> s.getStatus() != SlotStatus.CANCELLED).toList();
    }
}
