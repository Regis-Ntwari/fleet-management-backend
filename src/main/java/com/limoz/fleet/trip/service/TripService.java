package com.limoz.fleet.trip.service;

import com.limoz.fleet.trip.domain.Trip;
import com.limoz.fleet.trip.domain.TripCalculations;
import com.limoz.fleet.trip.domain.TripStatus;
import com.limoz.fleet.trip.mapper.TripMapper;
import com.limoz.fleet.trip.repository.TripRepository;
import com.limoz.fleet.trip.repository.TripSpecifications;

import com.limoz.fleet.audit.domain.AuditAction;
import com.limoz.fleet.audit.service.AuditService;
import com.limoz.fleet.common.api.PageResponse;
import com.limoz.fleet.common.event.OperationalEvent;
import com.limoz.fleet.common.event.Severity;
import com.limoz.fleet.common.exception.BusinessRuleException;
import com.limoz.fleet.common.exception.InvalidStateTransitionException;
import com.limoz.fleet.common.exception.ResourceNotFoundException;
import com.limoz.fleet.common.sequence.ReferenceNumberService;
import com.limoz.fleet.common.sequence.ReferenceType;
import com.limoz.fleet.config.CacheConfig;
import com.limoz.fleet.customer.service.CustomerService;
import com.limoz.fleet.document.service.DocumentService;
import com.limoz.fleet.driver.domain.Driver;
import com.limoz.fleet.driver.service.DriverService;
import com.limoz.fleet.driver.domain.DriverStatus;
import com.limoz.fleet.security.Roles;
import com.limoz.fleet.security.SecurityUtils;
import com.limoz.fleet.settings.domain.SettingKeys;
import com.limoz.fleet.settings.service.SettingsService;
import com.limoz.fleet.trip.dto.DeploymentTripCommand;
import com.limoz.fleet.trip.dto.TripCancelRequest;
import com.limoz.fleet.trip.dto.TripCompleteRequest;
import com.limoz.fleet.trip.dto.TripFilter;
import com.limoz.fleet.trip.dto.TripRequest;
import com.limoz.fleet.trip.dto.TripResponse;
import com.limoz.fleet.trip.dto.TripStartRequest;
import com.limoz.fleet.vehicle.service.OdometerService;
import com.limoz.fleet.vehicle.domain.OdometerSource;
import com.limoz.fleet.vehicle.domain.Vehicle;
import com.limoz.fleet.vehicle.service.VehicleService;
import com.limoz.fleet.vehicle.domain.VehicleStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Trip lifecycle: PLANNED -> DISPATCHED -> IN_PROGRESS -> COMPLETED (or CANCELLED). Ad hoc trips are
 * planned through the API; booking deployments open and close trips through
 * {@link #createForDeployment} / {@link #completeForDeployment}. Vehicle and driver statuses are only
 * touched while a trip is in progress.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TripService {

    public static final List<TripStatus> ACTIVE_STATUSES = List.of(TripStatus.PLANNED, TripStatus.DISPATCHED, TripStatus.IN_PROGRESS);
    private static final Set<DriverStatus> PLANNABLE_DRIVER_STATUSES =
            EnumSet.of(DriverStatus.AVAILABLE, DriverStatus.ASSIGNED, DriverStatus.ON_TRIP, DriverStatus.OFF_DUTY);

    private final TripRepository tripRepository;
    private final TripMapper mapper;
    private final VehicleService vehicleService;
    private final DriverService driverService;
    private final CustomerService customerService;
    private final DocumentService documentService;
    private final OdometerService odometerService;
    private final SettingsService settingsService;
    private final ReferenceNumberService referenceNumberService;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final ZoneId operationalZone;

    // ---------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public PageResponse<TripResponse> search(TripFilter filter, Pageable pageable) {
        return PageResponse.from(tripRepository.findAll(TripSpecifications.from(filter, operationalZone), pageable).map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    public TripResponse get(Long id) {
        return mapper.toResponse(load(id));
    }

    @Transactional(readOnly = true)
    public List<TripResponse> active() {
        return tripRepository.findByStatusOrderByStartedAtAsc(TripStatus.IN_PROGRESS).stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<TripResponse> forVehicle(Long vehicleId, Pageable pageable) {
        vehicleService.load(vehicleId);
        return search(new TripFilter(null, vehicleId, null, null, null, null, null, null), sortedByScheduleDesc(pageable));
    }

    @Transactional(readOnly = true)
    public PageResponse<TripResponse> forDriver(Long driverId, Pageable pageable) {
        driverService.load(driverId);
        return search(new TripFilter(null, null, driverId, null, null, null, null, null), sortedByScheduleDesc(pageable));
    }

    /** Trips of the driver profile linked to the current user (DRIVER role). */
    @Transactional(readOnly = true)
    public PageResponse<TripResponse> mine(List<TripStatus> statuses, Pageable pageable) {
        Long driverId = SecurityUtils.requireCurrentUser().driverId();
        if (driverId == null) {
            throw new BusinessRuleException("NO_DRIVER_PROFILE", "The current user is not linked to a driver profile");
        }
        return search(new TripFilter(null, null, driverId, null, null, statuses, null, null), sortedByScheduleDesc(pageable));
    }

    @Transactional(readOnly = true)
    public Trip load(Long id) {
        return tripRepository.findDetailedById(id).orElseThrow(() -> new ResourceNotFoundException("Trip", id));
    }

    /** Active trips (PLANNED / DISPATCHED / IN_PROGRESS) of a vehicle overlapping [from, to), excluding a trip id. */
    @Transactional(readOnly = true)
    public List<Trip> overlappingForVehicle(Long vehicleId, Instant from, Instant to, Long excludeTripId) {
        return tripRepository.findOverlappingForVehicle(vehicleId, from, to, ACTIVE_STATUSES, excludeTripId);
    }

    @Transactional(readOnly = true)
    public List<Trip> overlappingForDriver(Long driverId, Instant from, Instant to, Long excludeTripId) {
        return tripRepository.findOverlappingForDriver(driverId, from, to, ACTIVE_STATUSES, excludeTripId);
    }

    @Transactional(readOnly = true)
    public List<Trip> activeOverlapping(Instant from, Instant to) {
        return tripRepository.findOverlapping(from, to, ACTIVE_STATUSES);
    }

    // ---------------------------------------------------------------- ad hoc trips

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public TripResponse create(TripRequest request) {
        validateSchedule(request);
        Vehicle vehicle = vehicleService.loadActive(request.vehicleId());
        Driver driver = driverService.loadActive(request.driverId());
        validatePlanning(vehicle, driver, request.scheduledStartAt(), request.scheduledEndAt(), null);

        Trip trip = new Trip();
        trip.setTripNumber(referenceNumberService.next(ReferenceType.TRIP));
        trip.setVehicle(vehicle);
        trip.setDriver(driver);
        apply(trip, request);
        trip = tripRepository.save(trip);
        TripResponse response = mapper.toResponse(trip);
        auditService.record(AuditAction.CREATE, "Trip", trip.getId(), trip.getTripNumber(), null, response,
                "Trip " + trip.getTripNumber() + " planned for " + vehicle.getPlateNumber());
        return response;
    }

    public TripResponse update(Long id, TripRequest request) {
        Trip trip = load(id);
        if (trip.getStatus() != TripStatus.PLANNED) {
            throw new BusinessRuleException("TRIP_NOT_EDITABLE", "Only planned trips can be edited; trip is " + trip.getStatus());
        }
        validateSchedule(request);
        Vehicle vehicle = vehicleService.loadActive(request.vehicleId());
        Driver driver = driverService.loadActive(request.driverId());
        validatePlanning(vehicle, driver, request.scheduledStartAt(), request.scheduledEndAt(), id);
        TripResponse before = mapper.toResponse(trip);
        trip.setVehicle(vehicle);
        trip.setDriver(driver);
        apply(trip, request);
        TripResponse after = mapper.toResponse(tripRepository.save(trip));
        auditService.record(AuditAction.UPDATE, "Trip", id, trip.getTripNumber(), before, after, "Trip updated");
        return after;
    }

    /** PLANNED -> DISPATCHED: the crew has been notified; vehicle and driver statuses are unchanged until the trip starts. */
    public TripResponse dispatch(Long id) {
        Trip trip = load(id);
        requireTransition(trip, TripStatus.DISPATCHED);
        Vehicle vehicle = trip.getVehicle();
        if (!vehicle.getOperationalStatus().isDispatchable()) {
            throw new BusinessRuleException("VEHICLE_NOT_AVAILABLE",
                    "Vehicle " + vehicle.getPlateNumber() + " is " + vehicle.getOperationalStatus() + " and cannot be dispatched");
        }
        TripResponse before = mapper.toResponse(trip);
        trip.setStatus(TripStatus.DISPATCHED);
        TripResponse after = mapper.toResponse(tripRepository.save(trip));
        auditService.record(AuditAction.STATUS_CHANGE, "Trip", id, trip.getTripNumber(), before, after, "Trip dispatched");
        return after;
    }

    /** PLANNED / DISPATCHED -> IN_PROGRESS. Records the start odometer and puts vehicle and driver ON_TRIP. */
    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public TripResponse start(Long id, TripStartRequest request) {
        Trip trip = load(id);
        requireTransition(trip, TripStatus.IN_PROGRESS);
        Instant now = Instant.now(clock);
        Instant startedAt = request.startedAt() == null ? now : request.startedAt();
        if (startedAt.isAfter(now)) {
            throw new BusinessRuleException("START_IN_FUTURE", "Trip start time cannot be in the future");
        }
        Vehicle vehicle = trip.getVehicle();
        long startOdometer = request.startOdometerKm() == null ? vehicle.getOdometerKm() : request.startOdometerKm();
        TripResponse before = mapper.toResponse(trip);
        activate(trip, startOdometer, startedAt, OdometerSource.TRIP);
        TripResponse after = mapper.toResponse(tripRepository.save(trip));
        auditService.record(AuditAction.STATUS_CHANGE, "Trip", id, trip.getTripNumber(), before, after,
                "Trip " + trip.getTripNumber() + " started at " + startOdometer + " km");
        return after;
    }

    /** IN_PROGRESS -> COMPLETED with distance, duration and optional fuel / max speed. */
    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public TripResponse complete(Long id, TripCompleteRequest request) {
        Trip trip = load(id);
        requireTransition(trip, TripStatus.COMPLETED);
        Instant now = Instant.now(clock);
        Instant endedAt = request.endedAt() == null ? now : request.endedAt();
        if (endedAt.isAfter(now)) {
            throw new BusinessRuleException("END_IN_FUTURE", "Trip end time cannot be in the future");
        }
        TripResponse before = mapper.toResponse(trip);
        finish(trip, request.endOdometerKm(), endedAt, request.fuelUsedLitres(), request.maxSpeedKph(), OdometerSource.TRIP);
        if (request.notes() != null && !request.notes().isBlank()) {
            trip.setNotes(request.notes());
        }
        TripResponse after = mapper.toResponse(tripRepository.save(trip));
        auditService.record(AuditAction.COMPLETE, "Trip", id, trip.getTripNumber(), before, after,
                "Trip " + trip.getTripNumber() + " completed: " + trip.getDistanceKm() + " km");
        publishCompletion(trip);
        return after;
    }

    /** Any status except COMPLETED / CANCELLED -> CANCELLED. A trip in progress releases its vehicle and driver. */
    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public TripResponse cancel(Long id, TripCancelRequest request) {
        Trip trip = load(id);
        requireTransition(trip, TripStatus.CANCELLED);
        TripResponse before = mapper.toResponse(trip);
        if (trip.getStatus() == TripStatus.IN_PROGRESS) {
            release(trip, "Trip " + trip.getTripNumber() + " cancelled");
            trip.setEndedAt(Instant.now(clock));
        }
        trip.setStatus(TripStatus.CANCELLED);
        trip.setCancellationReason(request.reason());
        TripResponse after = mapper.toResponse(tripRepository.save(trip));
        auditService.record(AuditAction.CANCEL, "Trip", id, trip.getTripNumber(), before, after,
                "Trip " + trip.getTripNumber() + " cancelled: " + request.reason());
        return after;
    }

    // ---------------------------------------------------------------- booking deployments

    /**
     * Opens an IN_PROGRESS trip for a departing booking slot. Availability was validated when the slot was
     * assigned, so only the vehicle / driver status rules are re-checked here.
     */
    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public Trip createForDeployment(DeploymentTripCommand command) {
        Trip trip = new Trip();
        trip.setTripNumber(referenceNumberService.next(ReferenceType.TRIP));
        trip.setVehicle(command.vehicle());
        trip.setDriver(command.driver());
        trip.setCustomer(command.customer());
        trip.setBooking(command.booking());
        trip.setBookingSlotId(command.bookingSlotId());
        trip.setOrigin(command.origin());
        trip.setDestination(command.destination());
        trip.setPurpose(command.purpose());
        trip.setPassengers(command.passengers());
        trip.setScheduledStartAt(command.scheduledStartAt());
        trip.setScheduledEndAt(command.scheduledEndAt());
        trip.setNotes(command.notes());
        trip = tripRepository.save(trip);
        activate(trip, command.startOdometerKm(), command.startedAt(), OdometerSource.DEPLOYMENT);
        trip = tripRepository.save(trip);
        auditService.record(AuditAction.CREATE, "Trip", trip.getId(), trip.getTripNumber(), null, mapper.toResponse(trip),
                "Trip " + trip.getTripNumber() + " opened for booking " + command.booking().getBookingNumber());
        return trip;
    }

    /** Completes the trip of a returning booking slot (end odometer -> distance, duration, statuses restored). */
    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public Trip completeForDeployment(Long tripId, long endOdometerKm, Instant endedAt, String notes) {
        Trip trip = load(tripId);
        requireTransition(trip, TripStatus.COMPLETED);
        TripResponse before = mapper.toResponse(trip);
        finish(trip, endOdometerKm, endedAt, null, null, OdometerSource.DEPLOYMENT);
        if (notes != null && !notes.isBlank()) {
            trip.setNotes(notes);
        }
        trip = tripRepository.save(trip);
        auditService.record(AuditAction.COMPLETE, "Trip", trip.getId(), trip.getTripNumber(), before, mapper.toResponse(trip),
                "Trip " + trip.getTripNumber() + " completed on return: " + trip.getDistanceKm() + " km");
        publishCompletion(trip);
        return trip;
    }

    // ---------------------------------------------------------------- rules

    private void validateSchedule(TripRequest r) {
        if (r.scheduledEndAt() != null && r.scheduledEndAt().isBefore(r.scheduledStartAt())) {
            throw new BusinessRuleException("INVALID_SCHEDULE", "Scheduled end cannot precede the scheduled start");
        }
    }

    /**
     * Planning rules: operational vehicle with valid required documents, driver with a valid licence who is
     * not suspended / on leave / inactive, and no other active trip of the vehicle or driver in the window.
     * Overlaps with booking slots are enforced by the dispatch module when a slot is assigned.
     */
    private void validatePlanning(Vehicle vehicle, Driver driver, Instant start, Instant end, Long excludeTripId) {
        Instant windowEnd = end == null ? start : end;
        LocalDate startDate = LocalDate.ofInstant(start, operationalZone);
        if (!vehicle.getOperationalStatus().isOperational()) {
            throw new BusinessRuleException("VEHICLE_NOT_AVAILABLE",
                    "Vehicle " + vehicle.getPlateNumber() + " is " + vehicle.getOperationalStatus() + " and cannot be planned");
        }
        if (settingsService.getBoolean(SettingKeys.DISPATCH_REQUIRE_VALID_DOCUMENTS)) {
            List<String> problems = documentService.missingOrExpiredRequiredDocuments(vehicle.getId(), startDate);
            if (!problems.isEmpty()) {
                throw new BusinessRuleException("VEHICLE_DOCUMENTS_INVALID",
                        "Vehicle " + vehicle.getPlateNumber() + " cannot be dispatched: " + String.join(", ", problems));
            }
        }
        if (!PLANNABLE_DRIVER_STATUSES.contains(driver.getStatus())) {
            throw new BusinessRuleException("DRIVER_NOT_AVAILABLE",
                    "Driver " + driver.getFullName() + " is " + driver.getStatus() + " and cannot be planned");
        }
        if (settingsService.getBoolean(SettingKeys.DISPATCH_REQUIRE_VALID_LICENSE) && !driver.isLicenseValidOn(startDate)) {
            throw new BusinessRuleException("DRIVER_LICENSE_EXPIRED",
                    "Driver " + driver.getFullName() + "'s licence expired on " + driver.getLicenseExpiryDate());
        }
        List<Trip> vehicleClash = overlappingForVehicle(vehicle.getId(), start, windowEnd.plusMillis(1), excludeTripId);
        if (!vehicleClash.isEmpty()) {
            throw new BusinessRuleException("VEHICLE_DOUBLE_BOOKED",
                    "Vehicle " + vehicle.getPlateNumber() + " already has trip " + vehicleClash.getFirst().getTripNumber() + " in that window");
        }
        List<Trip> driverClash = overlappingForDriver(driver.getId(), start, windowEnd.plusMillis(1), excludeTripId);
        if (!driverClash.isEmpty()) {
            throw new BusinessRuleException("DRIVER_DOUBLE_BOOKED",
                    "Driver " + driver.getFullName() + " already has trip " + driverClash.getFirst().getTripNumber() + " in that window");
        }
    }

    /** Puts the trip in progress: status checks, odometer journal entry, vehicle and driver ON_TRIP. */
    private void activate(Trip trip, long startOdometerKm, Instant startedAt, OdometerSource source) {
        Vehicle vehicle = trip.getVehicle();
        Driver driver = trip.getDriver();
        if (vehicle.getOperationalStatus() == VehicleStatus.ON_TRIP) {
            throw new BusinessRuleException("VEHICLE_ON_TRIP", "Vehicle " + vehicle.getPlateNumber() + " is already on a trip");
        }
        if (vehicle.getOperationalStatus() == VehicleStatus.IN_MAINTENANCE) {
            throw new BusinessRuleException("VEHICLE_IN_MAINTENANCE", "Vehicle " + vehicle.getPlateNumber() + " is in maintenance");
        }
        if (!vehicle.getOperationalStatus().isDispatchable()) {
            throw new BusinessRuleException("VEHICLE_NOT_AVAILABLE",
                    "Vehicle " + vehicle.getPlateNumber() + " is " + vehicle.getOperationalStatus() + " and cannot start a trip");
        }
        if (!driver.getStatus().canBeAssigned()) {
            throw new BusinessRuleException("DRIVER_NOT_AVAILABLE",
                    "Driver " + driver.getFullName() + " is " + driver.getStatus() + " and cannot start a trip");
        }
        odometerService.record(vehicle, startOdometerKm, source, "Trip", trip.getId(), startedAt);
        trip.setStartOdometerKm(startOdometerKm);
        trip.setStartedAt(startedAt);
        trip.setStatus(TripStatus.IN_PROGRESS);
        vehicleService.transition(vehicle, VehicleStatus.ON_TRIP, "Trip " + trip.getTripNumber() + " started");
        driverService.transition(driver, DriverStatus.ON_TRIP, "Trip " + trip.getTripNumber() + " started");
    }

    /** Closes the trip: readings, distance, duration, odometer journal entry and resting statuses. */
    private void finish(Trip trip, long endOdometerKm, Instant endedAt, BigDecimal fuelUsedLitres, BigDecimal maxSpeedKph, OdometerSource source) {
        long startOdometer = trip.getStartOdometerKm() == null ? trip.getVehicle().getOdometerKm() : trip.getStartOdometerKm();
        trip.setDistanceKm(TripCalculations.distanceKm(startOdometer, endOdometerKm));
        trip.setDurationMinutes(TripCalculations.durationMinutes(trip.getStartedAt(), endedAt));
        odometerService.record(trip.getVehicle(), endOdometerKm, source, "Trip", trip.getId(), endedAt);
        trip.setEndOdometerKm(endOdometerKm);
        trip.setEndedAt(endedAt);
        trip.setFuelUsedLitres(fuelUsedLitres);
        trip.setMaxSpeedKph(maxSpeedKph);
        trip.setStatus(TripStatus.COMPLETED);
        release(trip, "Trip " + trip.getTripNumber() + " completed");
    }

    private void release(Trip trip, String reason) {
        Vehicle vehicle = trip.getVehicle();
        Driver driver = trip.getDriver();
        if (vehicle.getOperationalStatus() == VehicleStatus.ON_TRIP) {
            vehicleService.transition(vehicle, vehicleService.restingStatus(vehicle), reason);
        }
        if (driver.getStatus() == DriverStatus.ON_TRIP) {
            driverService.transition(driver, driverService.restingStatus(driver), reason);
        }
    }

    private void publishCompletion(Trip trip) {
        String link = "/trips/" + trip.getId();
        events.publishEvent(OperationalEvent.of("TRIP_COMPLETED", Severity.INFO, "Trip " + trip.getTripNumber() + " completed",
                trip.getVehicle().getPlateNumber() + " " + trip.getOrigin() + " -> " + trip.getDestination() + ", " + trip.getDistanceKm() + " km",
                "Trip", trip.getId(), trip.getTripNumber(), link, Roles.DISPATCHER, Roles.FLEET_MANAGER));
        if (trip.getMaxSpeedKph() != null) {
            BigDecimal limit = settingsService.getDecimal(SettingKeys.SPEED_LIMIT_KPH);
            if (trip.getMaxSpeedKph().compareTo(limit) > 0) {
                events.publishEvent(OperationalEvent.of("TRIP_OVER_SPEED", Severity.WARNING,
                        "Over-speed on trip " + trip.getTripNumber(),
                        trip.getDriver().getFullName() + " reached " + trip.getMaxSpeedKph() + " km/h (limit " + limit + " km/h) in " + trip.getVehicle().getPlateNumber(),
                        "Trip", trip.getId(), trip.getTripNumber(), link, Roles.FLEET_MANAGER, Roles.MANAGEMENT));
            }
        }
    }

    private void requireTransition(Trip trip, TripStatus to) {
        if (!trip.getStatus().canTransitionTo(to)) {
            throw new InvalidStateTransitionException("Trip " + trip.getTripNumber(), trip.getStatus(), to);
        }
    }

    private void apply(Trip t, TripRequest r) {
        t.setCustomer(r.customerId() == null ? null : customerService.loadActive(r.customerId()));
        t.setOrigin(r.origin().trim());
        t.setDestination(r.destination().trim());
        t.setRouteDescription(r.routeDescription());
        t.setPurpose(r.purpose());
        t.setPassengerDetails(r.passengerDetails());
        t.setPassengers(r.passengers());
        t.setScheduledStartAt(r.scheduledStartAt());
        t.setScheduledEndAt(r.scheduledEndAt());
        t.setNotes(r.notes());
    }

    private static Pageable sortedByScheduleDesc(Pageable pageable) {
        return pageable.getSort().isSorted() ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "scheduledStartAt"));
    }
}
