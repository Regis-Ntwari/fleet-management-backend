package com.limoz.fleet.booking.domain;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Per-vehicle deployment voucher (LIMOZ/000628/2026): the dispatch and billing document for one slot.
 * Created at departure, closed out when the return is recorded.
 */
@Entity
@Table(name = "deployment_vouchers")
@Getter
@Setter
@NoArgsConstructor
public class DeploymentVoucher extends BaseEntity {

    @Column(name = "voucher_number", nullable = false, unique = true, length = 30)
    private String voucherNumber;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_slot_id", nullable = false, unique = true)
    private BookingSlot slot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    /** Local purchase order covering this deployment (purchase orders are managed by the finance module). */
    @Column(name = "purchase_order_id")
    private Long purchaseOrderId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @Column(name = "account_manager_user_id")
    private Long accountManagerUserId;

    @Column(name = "voucher_date", nullable = false)
    private LocalDate voucherDate;

    @Column(length = 255)
    private String destination;

    @Column(name = "client_tel", length = 30)
    private String clientTel;

    @Column(name = "owner_name", length = 150)
    private String ownerName;

    @Column(name = "owner_driver_name", length = 120)
    private String ownerDriverName;

    @Column(name = "start_km")
    private Long startKm;

    @Column(name = "end_km")
    private Long endKm;

    @Column(name = "planned_days", nullable = false)
    private int plannedDays = 1;

    @Column(name = "effective_days", nullable = false, precision = 8, scale = 3)
    private BigDecimal effectiveDays = BigDecimal.ZERO;

    @Column(name = "day_rate", nullable = false, precision = 14, scale = 2)
    private BigDecimal dayRate = BigDecimal.ZERO;

    @Column(name = "institution_amount", nullable = false, precision = 16, scale = 2)
    private BigDecimal institutionAmount = BigDecimal.ZERO;

    @Column(name = "owner_amount", nullable = false, precision = 16, scale = 2)
    private BigDecimal ownerAmount = BigDecimal.ZERO;

    @Column(name = "fuel_amount", nullable = false, precision = 16, scale = 2)
    private BigDecimal fuelAmount = BigDecimal.ZERO;

    @Column(name = "net_amount", nullable = false, precision = 16, scale = 2)
    private BigDecimal netAmount = BigDecimal.ZERO;

    @Column(name = "mission_due_amount", nullable = false, precision = 16, scale = 2)
    private BigDecimal missionDueAmount = BigDecimal.ZERO;

    @Column(name = "po_amount", precision = 16, scale = 2)
    private BigDecimal poAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VoucherStatus status = VoucherStatus.ONGOING;

    @Column(length = 500)
    private String comment;

    @Column(columnDefinition = "text")
    private String observation;

    @Column(name = "returned_at")
    private Instant returnedAt;
}
