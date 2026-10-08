package com.limoz.fleet.booking.service;

import com.limoz.fleet.booking.domain.Booking;
import com.limoz.fleet.booking.domain.BookingSettingKeys;
import com.limoz.fleet.booking.domain.BookingSlot;
import com.limoz.fleet.booking.domain.SlotStatus;
import com.limoz.fleet.booking.mapper.BookingMapper;
import com.limoz.fleet.booking.repository.BookingSlotRepository;

import com.limoz.fleet.booking.dto.AvailabilityResponse;
import com.limoz.fleet.booking.dto.AvailabilityResponse.UnavailabilitySpan;
import com.limoz.fleet.booking.dto.AvailabilityResponse.UnavailabilityType;
import com.limoz.fleet.booking.dto.AvailabilityResponse.VehicleAvailability;
import com.limoz.fleet.booking.dto.BookingSlotResponse;
import com.limoz.fleet.booking.dto.DispatchBoardResponse;
import com.limoz.fleet.common.exception.BusinessRuleException;
import com.limoz.fleet.common.util.DateRanges;
import com.limoz.fleet.driver.service.DriverService;
import com.limoz.fleet.driver.domain.DriverStatus;
import com.limoz.fleet.driver.dto.DriverSummary;
import com.limoz.fleet.settings.service.SettingsService;
import com.limoz.fleet.trip.domain.Trip;
import com.limoz.fleet.trip.mapper.TripMapper;
import com.limoz.fleet.trip.service.TripService;
import com.limoz.fleet.trip.dto.TripResponse;
import com.limoz.fleet.vehicle.domain.Vehicle;
import com.limoz.fleet.vehicle.mapper.VehicleMapper;
import com.limoz.fleet.vehicle.repository.VehicleRepository;
import com.limoz.fleet.vehicle.service.VehicleService;
import com.limoz.fleet.vehicle.domain.VehicleStatus;
import com.limoz.fleet.vehicle.dto.VehicleSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Read models for the dispatcher: the daily board and the vehicle availability calendar. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DispatchBoardService {

    private static final List<VehicleStatus> DISPATCHABLE_VEHICLE_STATUSES = List.of(VehicleStatus.AVAILABLE, VehicleStatus.ASSIGNED, VehicleStatus.RESERVED);
    private static final List<DriverStatus> ASSIGNABLE_DRIVER_STATUSES = List.of(DriverStatus.AVAILABLE, DriverStatus.ASSIGNED);
    private static final List<SlotStatus> OPEN_STATUSES = List.of(SlotStatus.UNASSIGNED, SlotStatus.ASSIGNED);

    private final BookingSlotRepository slotRepository;
    private final VehicleRepository vehicleRepository;
    private final VehicleService vehicleService;
    private final DriverService driverService;
    private final TripService tripService;
    private final SettingsService settingsService;
    private final BookingMapper mapper;
    private final VehicleMapper vehicleMapper;
    private final TripMapper tripMapper;
    private final Clock clock;
    private final ZoneId operationalZone;

    public DispatchBoardResponse board(LocalDate date) {
        LocalDate day = date == null ? LocalDate.now(clock) : date;
        int horizon = settingsService.getInt(BookingSettingKeys.DISPATCH_BOARD_HORIZON_DAYS);

        List<BookingSlot> upcoming = new ArrayList<>(slotRepository.findStartingBetween(day, day.plusDays(horizon), OPEN_STATUSES));
        upcoming.sort(Comparator.comparing((BookingSlot s) -> s.getStatus() != SlotStatus.UNASSIGNED)
                .thenComparing(BookingSlot::getStartDate)
                .thenComparing(s -> s.getBooking().getId())
                .thenComparing(BookingSlot::getSlotNumber));
        List<BookingSlot> departures = upcoming.stream().filter(s -> s.getStartDate().equals(day)).toList();
        List<BookingSlot> returns = slotRepository.findEndingOn(day, List.of(SlotStatus.DEPLOYED));
        List<BookingSlot> assignedToday = slotRepository.findOverlapping(day, day, DispatchService.HOLDING_STATUSES);
        DateRanges.InstantRange dayRange = DateRanges.forDate(day, operationalZone);
        List<Trip> tripsToday = tripService.activeOverlapping(dayRange.from(), dayRange.to());
        List<TripResponse> currentTrips = tripService.active();

        Set<Long> busyVehicles = new HashSet<>();
        Set<Long> busyDrivers = new HashSet<>();
        assignedToday.forEach(s -> {
            if (s.getVehicle() != null) busyVehicles.add(s.getVehicle().getId());
            if (s.getDriver() != null) busyDrivers.add(s.getDriver().getId());
        });
        tripsToday.forEach(t -> {
            busyVehicles.add(t.getVehicle().getId());
            busyDrivers.add(t.getDriver().getId());
        });
        List<VehicleSummary> availableVehicles = vehicleService.summaries(DISPATCHABLE_VEHICLE_STATUSES).stream()
                .filter(v -> !busyVehicles.contains(v.id())).toList();
        List<DriverSummary> availableDrivers = driverService.summaries(ASSIGNABLE_DRIVER_STATUSES).stream()
                .filter(d -> !busyDrivers.contains(d.id())).toList();

        int unassigned = (int) upcoming.stream().filter(s -> s.getStatus() == SlotStatus.UNASSIGNED).count();
        DispatchBoardResponse.Counts counts = new DispatchBoardResponse.Counts(upcoming.size(), unassigned, departures.size(), returns.size(),
                currentTrips.size(), availableVehicles.size(), availableDrivers.size(), busyVehicles.size(), busyDrivers.size());
        return new DispatchBoardResponse(day, counts, toResponses(upcoming), toResponses(departures), toResponses(returns),
                toResponses(assignedToday), currentTrips, availableVehicles, availableDrivers);
    }

    /**
     * Per-vehicle unavailability spans in [from, to]: held or deployed booking slots, ad hoc trips, and - for
     * vehicles in maintenance or out of service - the whole range.
     */
    public AvailabilityResponse availability(LocalDate from, LocalDate to, Long categoryId) {
        LocalDate start = from == null ? LocalDate.now(clock) : from;
        LocalDate end = to == null ? start.plusDays(13) : to;
        if (end.isBefore(start)) {
            throw new BusinessRuleException("INVALID_DATE_RANGE", "Range end " + end + " is before its start " + start);
        }
        List<Vehicle> vehicles = vehicleRepository.findByArchivedFalseOrderByPlateNumberAsc().stream()
                .filter(v -> categoryId == null || v.getCategory().getId().equals(categoryId)).toList();
        Map<Long, List<BookingSlot>> slotsByVehicle = slotRepository.findOverlapping(start, end, DispatchService.HOLDING_STATUSES).stream()
                .filter(s -> s.getVehicle() != null)
                .collect(Collectors.groupingBy(s -> s.getVehicle().getId()));
        DateRanges.InstantRange window = DateRanges.between(start, end, operationalZone);
        Map<Long, List<Trip>> tripsByVehicle = tripService.activeOverlapping(window.from(), window.to()).stream()
                .filter(t -> t.getBookingSlotId() == null)
                .collect(Collectors.groupingBy(t -> t.getVehicle().getId()));

        List<VehicleAvailability> rows = new ArrayList<>();
        for (Vehicle vehicle : vehicles) {
            List<UnavailabilitySpan> spans = new ArrayList<>();
            VehicleStatus status = vehicle.getOperationalStatus();
            if (status == VehicleStatus.IN_MAINTENANCE) {
                spans.add(new UnavailabilitySpan(start, end, UnavailabilityType.MAINTENANCE, "In maintenance", "Vehicle", vehicle.getId(), vehicle.getPlateNumber()));
            } else if (!status.isOperational()) {
                spans.add(new UnavailabilitySpan(start, end, UnavailabilityType.OUT_OF_SERVICE, "Out of service", "Vehicle", vehicle.getId(), vehicle.getPlateNumber()));
            }
            for (BookingSlot slot : slotsByVehicle.getOrDefault(vehicle.getId(), List.of())) {
                boolean deployed = slot.getStatus() == SlotStatus.DEPLOYED;
                String customer = slot.getBooking().getCustomer().getName();
                spans.add(new UnavailabilitySpan(clamp(slot.getStartDate(), start, end), clamp(slot.getEndDate(), start, end),
                        deployed ? UnavailabilityType.DEPLOYED : UnavailabilityType.RESERVED,
                        (deployed ? "Deployed · " : "Reserved · ") + customer,
                        "Booking", slot.getBooking().getId(), slot.getBooking().getBookingNumber()));
            }
            for (Trip trip : tripsByVehicle.getOrDefault(vehicle.getId(), List.of())) {
                LocalDate tripStart = DateRanges.toLocalDate(trip.getScheduledStartAt(), operationalZone);
                LocalDate tripEnd = DateRanges.toLocalDate(trip.scheduledWindowEnd(), operationalZone);
                spans.add(new UnavailabilitySpan(clamp(tripStart, start, end), clamp(tripEnd, start, end), UnavailabilityType.TRIP,
                        "Trip · " + trip.getDestination(), "Trip", trip.getId(), trip.getTripNumber()));
            }
            spans.sort(Comparator.comparing(UnavailabilitySpan::from).thenComparing(UnavailabilitySpan::to));
            rows.add(new VehicleAvailability(vehicleMapper.toSummary(vehicle), vehicle.getCategory().getId(), status, spans.isEmpty(), spans));
        }
        return new AvailabilityResponse(start, end, rows.size(), rows);
    }

    private List<BookingSlotResponse> toResponses(List<BookingSlot> slots) {
        return slots.stream().map(mapper::toResponse).toList();
    }

    private static LocalDate clamp(LocalDate value, LocalDate min, LocalDate max) {
        if (value.isBefore(min)) return min;
        if (value.isAfter(max)) return max;
        return value;
    }
}
