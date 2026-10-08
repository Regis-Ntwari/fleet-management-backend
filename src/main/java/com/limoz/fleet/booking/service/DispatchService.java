package com.limoz.fleet.booking.service;

import com.limoz.fleet.booking.domain.Booking;
import com.limoz.fleet.booking.domain.BookingCalculations;
import com.limoz.fleet.booking.domain.BookingLine;
import com.limoz.fleet.booking.domain.BookingSlot;
import com.limoz.fleet.booking.domain.BookingStatus;
import com.limoz.fleet.booking.domain.DeploymentVoucher;
import com.limoz.fleet.booking.domain.SlotStatus;
import com.limoz.fleet.booking.domain.VoucherStatus;
import com.limoz.fleet.booking.mapper.BookingMapper;
import com.limoz.fleet.booking.repository.BookingSlotRepository;
import com.limoz.fleet.booking.repository.DeploymentVoucherRepository;

import com.limoz.fleet.audit.domain.AuditAction;
import com.limoz.fleet.audit.service.AuditService;
import com.limoz.fleet.booking.dto.AssignSlotRequest;
import com.limoz.fleet.booking.dto.BookingSlotResponse;
import com.limoz.fleet.booking.dto.DepartRequest;
import com.limoz.fleet.booking.dto.ReturnRequest;
import com.limoz.fleet.booking.dto.VoucherResponse;
import com.limoz.fleet.common.event.Severity;
import com.limoz.fleet.common.exception.BusinessRuleException;
import com.limoz.fleet.common.exception.ResourceNotFoundException;
import com.limoz.fleet.common.sequence.ReferenceNumberService;
import com.limoz.fleet.common.sequence.ReferenceType;
import com.limoz.fleet.common.util.DateRanges;
import com.limoz.fleet.config.CacheConfig;
import com.limoz.fleet.customer.domain.Customer;
import com.limoz.fleet.document.service.DocumentService;
import com.limoz.fleet.driver.domain.Driver;
import com.limoz.fleet.driver.service.DriverService;
import com.limoz.fleet.security.SecurityUtils;
import com.limoz.fleet.settings.domain.SettingKeys;
import com.limoz.fleet.settings.service.SettingsService;
import com.limoz.fleet.trip.domain.Trip;
import com.limoz.fleet.trip.service.TripService;
import com.limoz.fleet.trip.dto.DeploymentTripCommand;
import com.limoz.fleet.vehicle.domain.OwnershipType;
import com.limoz.fleet.vehicle.domain.Vehicle;
import com.limoz.fleet.vehicle.service.VehicleService;
import com.limoz.fleet.vehicle.domain.VehicleStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Deployment of booking slots: assign / replace vehicle and driver, record departures (which open a trip and
 * a deployment voucher) and returns (which close them). Every slot change re-derives the booking status.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DispatchService {

    public static final List<SlotStatus> HOLDING_STATUSES = List.of(SlotStatus.ASSIGNED, SlotStatus.DEPLOYED);

    private final BookingSlotRepository slotRepository;
    private final DeploymentVoucherRepository voucherRepository;
    private final BookingService bookingService;
    private final TripService tripService;
    private final VehicleService vehicleService;
    private final DriverService driverService;
    private final DocumentService documentService;
    private final SettingsService settingsService;
    private final ReferenceNumberService referenceNumberService;
    private final BookingMapper mapper;
    private final AuditService auditService;
    private final Clock clock;
    private final ZoneId operationalZone;

    @Transactional(readOnly = true)
    public BookingSlotResponse getSlot(Long slotId) {
        return mapper.toResponse(loadSlot(slotId));
    }

    @Transactional(readOnly = true)
    public BookingSlot loadSlot(Long slotId) {
        return slotRepository.findDetailedById(slotId).orElseThrow(() -> new ResourceNotFoundException("Booking slot", slotId));
    }

    // ---------------------------------------------------------------- assignment

    /** Assigns (or replaces) the vehicle and driver of a slot after availability and compliance checks. */
    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public BookingSlotResponse assignSlot(Long slotId, AssignSlotRequest request) {
        BookingSlot slot = loadSlot(slotId);
        Booking booking = slot.getBooking();
        requireDispatchable(booking);
        if (slot.getStatus() != SlotStatus.UNASSIGNED && slot.getStatus() != SlotStatus.ASSIGNED) {
            throw new BusinessRuleException("SLOT_NOT_ASSIGNABLE", "Slot #" + slot.getSlotNumber() + " is " + slot.getStatus());
        }
        Vehicle vehicle = vehicleService.loadActive(request.vehicleId());
        Driver driver = driverService.loadActive(request.driverId());
        validateVehicle(vehicle, slot, request.allowCategoryMismatch());
        validateDriver(driver, slot);

        BookingSlotResponse before = mapper.toResponse(slot);
        slot.setVehicle(vehicle);
        slot.setDriver(driver);
        if (request.shift() != null) {
            slot.setShift(request.shift());
        }
        if (request.notes() != null) {
            slot.setNotes(request.notes());
        }
        slot.setStatus(SlotStatus.ASSIGNED);
        slot.setAssignedAt(Instant.now(clock));
        slot.setAssignedByUserId(SecurityUtils.currentUserId().orElse(null));
        BookingSlotResponse after = mapper.toResponse(slotRepository.save(slot));
        auditService.record(AuditAction.ASSIGN, "BookingSlot", slot.getId(), booking.getBookingNumber() + "#" + slot.getSlotNumber(),
                before, after, vehicle.getPlateNumber() + " / " + driver.getFullName() + " assigned to " + booking.getBookingNumber() + " slot #" + slot.getSlotNumber());
        bookingService.refreshStatusFromSlots(booking);
        if (booking.activeSlots().stream().noneMatch(s -> s.getStatus() == SlotStatus.UNASSIGNED)) {
            bookingService.publish("BOOKING_ASSIGNED", Severity.INFO, "Booking " + booking.getBookingNumber() + " fully assigned",
                    "All " + booking.activeSlots().size() + " vehicle(s) for " + booking.getCustomer().getName() + " are assigned and ready to dispatch", booking);
        }
        return after;
    }

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public BookingSlotResponse unassignSlot(Long slotId) {
        BookingSlot slot = loadSlot(slotId);
        if (slot.getStatus() != SlotStatus.ASSIGNED) {
            throw new BusinessRuleException("SLOT_NOT_ASSIGNED", "Slot #" + slot.getSlotNumber() + " is " + slot.getStatus() + " and cannot be unassigned");
        }
        BookingSlotResponse before = mapper.toResponse(slot);
        String released = slot.getVehicle().getPlateNumber() + " / " + slot.getDriver().getFullName();
        slot.setVehicle(null);
        slot.setDriver(null);
        slot.setAssignedAt(null);
        slot.setAssignedByUserId(null);
        slot.setStatus(SlotStatus.UNASSIGNED);
        BookingSlotResponse after = mapper.toResponse(slotRepository.save(slot));
        Booking booking = slot.getBooking();
        auditService.record(AuditAction.UNASSIGN, "BookingSlot", slot.getId(), booking.getBookingNumber() + "#" + slot.getSlotNumber(),
                before, after, released + " released from " + booking.getBookingNumber() + " slot #" + slot.getSlotNumber());
        bookingService.refreshStatusFromSlots(booking);
        return after;
    }

    // ---------------------------------------------------------------- departure / return

    /** Vehicle leaves: opens the trip, issues the deployment voucher and puts vehicle, driver and booking on the road. */
    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public VoucherResponse depart(Long slotId, DepartRequest request) {
        BookingSlot slot = loadSlot(slotId);
        Booking booking = slot.getBooking();
        requireDispatchable(booking);
        if (slot.getStatus() == SlotStatus.DEPLOYED) {
            throw new BusinessRuleException("SLOT_ALREADY_DEPARTED", "Slot #" + slot.getSlotNumber() + " already departed on " + slot.getDepartedAt());
        }
        if (slot.getStatus() != SlotStatus.ASSIGNED) {
            throw new BusinessRuleException("SLOT_NOT_ASSIGNED", "Assign a vehicle and driver to slot #" + slot.getSlotNumber() + " before departure");
        }
        Instant now = Instant.now(clock);
        Instant departedAt = request.departedAt() == null ? now : request.departedAt();
        if (departedAt.isAfter(now)) {
            throw new BusinessRuleException("DEPARTURE_IN_FUTURE", "Departure time cannot be in the future");
        }
        Vehicle vehicle = slot.getVehicle();
        Driver driver = slot.getDriver();
        Customer customer = booking.getCustomer();
        long odometerOut = request.odometerOut() == null ? vehicle.getOdometerKm() : request.odometerOut();
        String destination = firstNonBlank(request.destination(), booking.getDropoffLocation(), "Deployment for " + customer.getName());
        String origin = firstNonBlank(booking.getPickupLocation(), customer.getCity(), "Base");

        Trip trip = tripService.createForDeployment(new DeploymentTripCommand(vehicle, driver, customer, booking, slot.getId(), origin,
                destination, booking.getServiceType() + " booking " + booking.getBookingNumber(), booking.getPassengers(),
                at(slot.getStartDate(), booking.getPickupTime(), LocalTime.MIDNIGHT),
                at(slot.getEndDate(), booking.getReturnTime(), LocalTime.of(23, 59, 59)),
                odometerOut, departedAt, request.notes()));

        BookingSlotResponse before = mapper.toResponse(slot);
        slot.setStatus(SlotStatus.DEPLOYED);
        slot.setDepartedAt(departedAt);
        slot.setOdometerOut(odometerOut);
        slot.setTripId(trip.getId());
        if (request.notes() != null) {
            slot.setNotes(request.notes());
        }
        slotRepository.save(slot);
        auditService.record(AuditAction.STATUS_CHANGE, "BookingSlot", slot.getId(), booking.getBookingNumber() + "#" + slot.getSlotNumber(),
                before, mapper.toResponse(slot), vehicle.getPlateNumber() + " departed for " + booking.getBookingNumber() + " slot #" + slot.getSlotNumber());

        DeploymentVoucher voucher = issueVoucher(slot, booking, customer, vehicle, driver, destination, odometerOut, departedAt);
        bookingService.refreshStatusFromSlots(booking);
        bookingService.publish("VEHICLE_DEPARTED", Severity.INFO, vehicle.getPlateNumber() + " departed for " + customer.getName(),
                "Voucher " + voucher.getVoucherNumber() + ", driver " + driver.getFullName() + ", " + odometerOut + " km at departure", booking);
        return mapper.toResponse(voucher);
    }

    /** Vehicle is back: closes the trip, prices the voucher and restores vehicle and driver statuses. */
    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public VoucherResponse recordReturn(Long slotId, ReturnRequest request) {
        BookingSlot slot = loadSlot(slotId);
        if (slot.getStatus() != SlotStatus.DEPLOYED) {
            throw new BusinessRuleException("SLOT_NOT_DEPLOYED", "Slot #" + slot.getSlotNumber() + " is " + slot.getStatus() + "; only deployed vehicles can be returned");
        }
        VoucherStatus status = request.status() == null ? VoucherStatus.RETURNED : request.status();
        if (status != VoucherStatus.RETURNED && status != VoucherStatus.NOT_RETURNED) {
            throw new BusinessRuleException("INVALID_RETURN_STATUS", "Return status must be RETURNED or NOT_RETURNED");
        }
        Instant now = Instant.now(clock);
        Instant returnedAt = request.returnedAt() == null ? now : request.returnedAt();
        if (returnedAt.isAfter(now)) {
            throw new BusinessRuleException("RETURN_IN_FUTURE", "Return time cannot be in the future");
        }
        if (returnedAt.isBefore(slot.getDepartedAt())) {
            throw new BusinessRuleException("RETURN_BEFORE_DEPARTURE", "Return time cannot precede the departure at " + slot.getDepartedAt());
        }
        if (slot.getOdometerOut() != null && request.odometerIn() < slot.getOdometerOut()) {
            throw new BusinessRuleException("END_ODOMETER_BELOW_START",
                    "End odometer " + request.odometerIn() + " km cannot be lower than the start reading of " + slot.getOdometerOut() + " km");
        }
        DeploymentVoucher voucher = voucherRepository.findBySlotId(slot.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Deployment voucher for slot " + slotId + " was not found"));
        Booking booking = slot.getBooking();

        tripService.completeForDeployment(slot.getTripId(), request.odometerIn(), returnedAt, request.comment());

        BookingSlotResponse slotBefore = mapper.toResponse(slot);
        slot.setStatus(SlotStatus.RETURNED);
        slot.setReturnedAt(returnedAt);
        slot.setOdometerIn(request.odometerIn());
        slotRepository.save(slot);
        auditService.record(AuditAction.STATUS_CHANGE, "BookingSlot", slot.getId(), booking.getBookingNumber() + "#" + slot.getSlotNumber(),
                slotBefore, mapper.toResponse(slot), slot.getVehicle().getPlateNumber() + " returned from " + booking.getBookingNumber() + " slot #" + slot.getSlotNumber());

        VoucherResponse before = mapper.toResponse(voucher);
        voucher.setEndKm(request.odometerIn());
        voucher.setReturnedAt(returnedAt);
        voucher.setEffectiveDays(BookingCalculations.effectiveDays(slot.getDepartedAt(), returnedAt));
        voucher.setInstitutionAmount(BookingCalculations.institutionAmount(voucher.getEffectiveDays(), voucher.getDayRate()));
        voucher.setFuelAmount(orZero(request.fuelAmount()));
        voucher.setMissionDueAmount(orZero(request.missionDueAmount()));
        if (request.ownerAmount() != null) {
            voucher.setOwnerAmount(request.ownerAmount());
        }
        voucher.setNetAmount(BookingCalculations.netAmount(voucher.getOwnerAmount(), voucher.getFuelAmount()));
        voucher.setStatus(status);
        voucher.setComment(request.comment());
        voucher.setObservation(request.observation());
        VoucherResponse after = mapper.toResponse(voucherRepository.save(voucher));
        auditService.record(AuditAction.COMPLETE, "DeploymentVoucher", voucher.getId(), voucher.getVoucherNumber(), before, after,
                "Return recorded on " + voucher.getVoucherNumber() + ": " + voucher.getEffectiveDays() + " day(s), " + voucher.getInstitutionAmount());
        bookingService.refreshStatusFromSlots(booking);
        bookingService.publish("VEHICLE_RETURNED", status == VoucherStatus.RETURNED ? Severity.INFO : Severity.WARNING,
                slot.getVehicle().getPlateNumber() + (status == VoucherStatus.RETURNED ? " returned from " : " NOT returned from ") + booking.getCustomer().getName(),
                "Voucher " + voucher.getVoucherNumber() + ": " + (request.odometerIn() - slot.getOdometerOut()) + " km, " + voucher.getEffectiveDays() + " day(s)", booking);
        return after;
    }

    // ---------------------------------------------------------------- validation rules

    /**
     * A vehicle can be assigned when it is dispatchable (not in maintenance / out of service / on a trip),
     * matches the booked category (unless overridden), has no other slot or trip in the slot's date range and
     * - when required by settings - holds valid required documents on the slot start date.
     */
    private void validateVehicle(Vehicle vehicle, BookingSlot slot, boolean allowCategoryMismatch) {
        VehicleStatus status = vehicle.getOperationalStatus();
        if (status == VehicleStatus.IN_MAINTENANCE) {
            throw new BusinessRuleException("VEHICLE_IN_MAINTENANCE", "Vehicle " + vehicle.getPlateNumber() + " is in maintenance");
        }
        if (!status.isDispatchable()) {
            throw new BusinessRuleException("VEHICLE_NOT_AVAILABLE", "Vehicle " + vehicle.getPlateNumber() + " is " + status + " and cannot be assigned");
        }
        if (!allowCategoryMismatch && !vehicle.getCategory().getId().equals(slot.getCategory().getId())) {
            throw new BusinessRuleException("CATEGORY_MISMATCH", "Vehicle " + vehicle.getPlateNumber() + " is a " + vehicle.getCategory().getName()
                    + " but the slot requires a " + slot.getCategory().getName() + " (set allowCategoryMismatch to override)");
        }
        List<BookingSlot> slotClash = slotRepository.findOverlappingForVehicle(vehicle.getId(), slot.getStartDate(), slot.getEndDate(), HOLDING_STATUSES, slot.getId());
        if (!slotClash.isEmpty()) {
            BookingSlot other = slotClash.getFirst();
            throw new BusinessRuleException("VEHICLE_DOUBLE_BOOKED", "Vehicle " + vehicle.getPlateNumber() + " is already held by booking "
                    + other.getBooking().getBookingNumber() + " from " + other.getStartDate() + " to " + other.getEndDate());
        }
        DateRanges.InstantRange window = DateRanges.between(slot.getStartDate(), slot.getEndDate(), operationalZone);
        List<Trip> tripClash = tripService.overlappingForVehicle(vehicle.getId(), window.from(), window.to(), null).stream()
                .filter(t -> !slot.getId().equals(t.getBookingSlotId())).toList();
        if (!tripClash.isEmpty()) {
            throw new BusinessRuleException("VEHICLE_DOUBLE_BOOKED", "Vehicle " + vehicle.getPlateNumber() + " has trip "
                    + tripClash.getFirst().getTripNumber() + " scheduled in that period");
        }
        if (settingsService.getBoolean(SettingKeys.DISPATCH_REQUIRE_VALID_DOCUMENTS)) {
            List<String> problems = documentService.missingOrExpiredRequiredDocuments(vehicle.getId(), slot.getStartDate());
            if (!problems.isEmpty()) {
                throw new BusinessRuleException("VEHICLE_DOCUMENTS_INVALID",
                        "Vehicle " + vehicle.getPlateNumber() + " cannot be dispatched: " + String.join(", ", problems));
            }
        }
    }

    private void validateDriver(Driver driver, BookingSlot slot) {
        if (!driver.getStatus().canBeAssigned()) {
            throw new BusinessRuleException("DRIVER_NOT_AVAILABLE", "Driver " + driver.getFullName() + " is " + driver.getStatus() + " and cannot be assigned");
        }
        if (settingsService.getBoolean(SettingKeys.DISPATCH_REQUIRE_VALID_LICENSE) && !driver.isLicenseValidOn(slot.getStartDate())) {
            throw new BusinessRuleException("DRIVER_LICENSE_EXPIRED", "Driver " + driver.getFullName() + "'s licence expired on " + driver.getLicenseExpiryDate());
        }
        List<BookingSlot> slotClash = slotRepository.findOverlappingForDriver(driver.getId(), slot.getStartDate(), slot.getEndDate(), HOLDING_STATUSES, slot.getId());
        if (!slotClash.isEmpty()) {
            BookingSlot other = slotClash.getFirst();
            throw new BusinessRuleException("DRIVER_DOUBLE_BOOKED", "Driver " + driver.getFullName() + " is already held by booking "
                    + other.getBooking().getBookingNumber() + " from " + other.getStartDate() + " to " + other.getEndDate());
        }
        DateRanges.InstantRange window = DateRanges.between(slot.getStartDate(), slot.getEndDate(), operationalZone);
        List<Trip> tripClash = tripService.overlappingForDriver(driver.getId(), window.from(), window.to(), null).stream()
                .filter(t -> !slot.getId().equals(t.getBookingSlotId())).toList();
        if (!tripClash.isEmpty()) {
            throw new BusinessRuleException("DRIVER_DOUBLE_BOOKED", "Driver " + driver.getFullName() + " has trip "
                    + tripClash.getFirst().getTripNumber() + " scheduled in that period");
        }
    }

    private void requireDispatchable(Booking booking) {
        if (!booking.getStatus().isDispatchable()) {
            String hint = booking.getStatus() == BookingStatus.DRAFT || booking.getStatus() == BookingStatus.REQUESTED
                    ? "; confirm the booking first" : "";
            throw new BusinessRuleException("BOOKING_NOT_DISPATCHABLE", "Booking " + booking.getBookingNumber() + " is " + booking.getStatus() + hint);
        }
    }

    // ---------------------------------------------------------------- voucher issue

    /**
     * Day rate: the line's unit price for daily pricing, otherwise the vehicle's own day rate or its
     * category default. Owner fields are filled for third-party (owner-supplied) vehicles.
     */
    private DeploymentVoucher issueVoucher(BookingSlot slot, Booking booking, Customer customer, Vehicle vehicle, Driver driver,
                                           String destination, long odometerOut, Instant departedAt) {
        BookingLine line = slot.getLine();
        BigDecimal dayRate = line.getPricingType().isDaily() ? line.getUnitPrice()
                : vehicle.getDayRate() != null ? vehicle.getDayRate()
                : vehicle.getCategory().getDefaultDayRate() != null ? vehicle.getCategory().getDefaultDayRate() : BigDecimal.ZERO;
        DeploymentVoucher voucher = new DeploymentVoucher();
        voucher.setVoucherNumber(referenceNumberService.next(ReferenceType.DEPLOYMENT_VOUCHER));
        voucher.setSlot(slot);
        voucher.setBooking(booking);
        voucher.setCustomer(customer);
        voucher.setVehicle(vehicle);
        voucher.setDriver(driver);
        voucher.setAccountManagerUserId(customer.getAccountManagerUserId());
        voucher.setVoucherDate(LocalDate.ofInstant(departedAt, operationalZone));
        voucher.setDestination(destination);
        voucher.setClientTel(firstNonBlank(booking.getContactPhone(), customer.getPhone(), null));
        if (vehicle.getOwnershipType() == OwnershipType.THIRD_PARTY) {
            voucher.setOwnerName(vehicle.getOwnerName());
            voucher.setOwnerDriverName(vehicle.getOwnerDriverName());
        }
        voucher.setStartKm(odometerOut);
        voucher.setPlannedDays(BookingCalculations.inclusiveDays(slot.getStartDate(), slot.getEndDate()));
        voucher.setDayRate(dayRate);
        voucher.setStatus(VoucherStatus.ONGOING);
        voucher = voucherRepository.save(voucher);
        auditService.record(AuditAction.CREATE, "DeploymentVoucher", voucher.getId(), voucher.getVoucherNumber(), null, mapper.toResponse(voucher),
                "Voucher " + voucher.getVoucherNumber() + " issued for " + vehicle.getPlateNumber() + " on " + booking.getBookingNumber());
        return voucher;
    }

    private Instant at(LocalDate date, LocalTime time, LocalTime fallback) {
        return date.atTime(time == null ? fallback : time).atZone(operationalZone).toInstant();
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String firstNonBlank(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) return first.trim();
        if (second != null && !second.isBlank()) return second.trim();
        return fallback;
    }
}
