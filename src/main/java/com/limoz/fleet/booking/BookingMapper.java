package com.limoz.fleet.booking;

import com.limoz.fleet.booking.dto.BookingLineResponse;
import com.limoz.fleet.booking.dto.BookingResponse;
import com.limoz.fleet.booking.dto.BookingSlotResponse;
import com.limoz.fleet.booking.dto.BookingSummary;
import com.limoz.fleet.booking.dto.ExtraChargeResponse;
import com.limoz.fleet.booking.dto.VoucherResponse;
import com.limoz.fleet.customer.CustomerMapper;
import com.limoz.fleet.driver.DriverMapper;
import com.limoz.fleet.vehicle.VehicleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
public class BookingMapper {

    private final CustomerMapper customerMapper;
    private final VehicleMapper vehicleMapper;
    private final DriverMapper driverMapper;

    public BookingResponse toResponse(Booking b, String commitmentReference, List<BookingSlot> slots, List<DeploymentVoucher> vouchers) {
        BigDecimal linesTotal = b.getLines().stream().map(BookingLine::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal extrasTotal = b.getExtraCharges().stream().map(BookingExtraCharge::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<BookingSlot> active = slots.stream().filter(s -> s.getStatus() != SlotStatus.CANCELLED).toList();
        int assigned = (int) active.stream().filter(s -> s.getStatus() != SlotStatus.UNASSIGNED).count();
        return new BookingResponse(b.getId(), b.getBookingNumber(), customerMapper.toSummary(b.getCustomer()), b.getCommitmentId(),
                commitmentReference, b.getContactName(), b.getContactPhone(), b.getContactEmail(), b.getServiceType(),
                b.getPickupLocation(), b.getDropoffLocation(), b.getStartDate(), b.getEndDate(), b.getPickupTime(), b.getReturnTime(),
                b.getPassengers(), b.getCurrency(), linesTotal, extrasTotal, b.getTotalAmount(), b.getStatus(), b.getSource(),
                b.getCancellationReason(), b.getConfirmedAt(), b.getDeployedAt(), b.getCompletedAt(), b.getCancelledAt(), b.getNotes(),
                active.size(), assigned,
                b.getLines().stream().map(this::toResponse).toList(),
                slots.stream().map(this::toResponse).toList(),
                b.getExtraCharges().stream().map(this::toResponse).toList(),
                vouchers.stream().map(this::toResponse).toList(),
                b.getCreatedAt(), b.getUpdatedAt(), b.getCreatedBy(), b.getUpdatedBy());
    }

    public BookingSummary toSummary(Booking b, int vehiclesRequested, int slotsAssigned) {
        return new BookingSummary(b.getId(), b.getBookingNumber(), b.getCustomer().getId(), b.getCustomer().getName(), b.getCommitmentId(),
                b.getServiceType(), b.getStartDate(), b.getEndDate(), b.getCurrency(), b.getTotalAmount(), b.getStatus(), b.getSource(),
                vehiclesRequested, slotsAssigned, b.getCreatedAt(), b.getUpdatedAt());
    }

    public BookingLineResponse toResponse(BookingLine l) {
        return new BookingLineResponse(l.getId(), l.getCategory().getId(), l.getCategory().getName(), l.getPreferredModel(), l.getQuantity(),
                l.getPricingType(), l.getStartDate(), l.getEndDate(),
                BookingCalculations.billableUnits(l.getPricingType(), l.getStartDate(), l.getEndDate()),
                l.getUnitPrice(), l.getLineTotal(), l.getNotes());
    }

    public BookingSlotResponse toResponse(BookingSlot s) {
        Booking b = s.getBooking();
        return new BookingSlotResponse(s.getId(), b.getId(), b.getBookingNumber(), b.getCustomer().getId(), b.getCustomer().getName(),
                s.getLine().getId(), s.getSlotNumber(), s.getCategory().getId(), s.getCategory().getName(), s.getLine().getPreferredModel(),
                s.getStartDate(), s.getEndDate(), s.getShift(), s.getStatus(), vehicleMapper.toSummary(s.getVehicle()),
                driverMapper.toSummary(s.getDriver()), s.getAssignedAt(), s.getAssignedByUserId(), s.getDepartedAt(), s.getReturnedAt(),
                s.getOdometerOut(), s.getOdometerIn(), s.getTripId(), s.getNotes());
    }

    public ExtraChargeResponse toResponse(BookingExtraCharge c) {
        return new ExtraChargeResponse(c.getId(), c.getDescription(), c.getAmount(), c.getCreatedAt(), c.getCreatedBy());
    }

    public VoucherResponse toResponse(DeploymentVoucher v) {
        BookingSlot slot = v.getSlot();
        Long distance = v.getStartKm() != null && v.getEndKm() != null ? v.getEndKm() - v.getStartKm() : null;
        return new VoucherResponse(v.getId(), v.getVoucherNumber(), slot.getId(), slot.getSlotNumber(), v.getBooking().getId(),
                v.getBooking().getBookingNumber(), customerMapper.toSummary(v.getCustomer()), v.getPurchaseOrderId(),
                vehicleMapper.toSummary(v.getVehicle()), driverMapper.toSummary(v.getDriver()), v.getAccountManagerUserId(),
                v.getVoucherDate(), v.getDestination(), v.getClientTel(), v.getOwnerName(), v.getOwnerDriverName(), v.getStartKm(),
                v.getEndKm(), distance, v.getPlannedDays(), v.getEffectiveDays(), v.getDayRate(), v.getInstitutionAmount(),
                v.getOwnerAmount(), v.getFuelAmount(), v.getNetAmount(), v.getMissionDueAmount(), v.getPoAmount(), v.getStatus(),
                v.getComment(), v.getObservation(), slot.getTripId(), slot.getDepartedAt(), v.getReturnedAt(), v.getCreatedAt(), v.getUpdatedAt());
    }
}
