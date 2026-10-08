package com.limoz.fleet.booking.dto;

import com.limoz.fleet.driver.dto.DriverSummary;
import com.limoz.fleet.trip.dto.TripResponse;
import com.limoz.fleet.vehicle.dto.VehicleSummary;

import java.time.LocalDate;
import java.util.List;

/** Dispatcher board for one operational day. */
public record DispatchBoardResponse(
        LocalDate date,
        Counts counts,
        List<BookingSlotResponse> upcomingJobs,
        List<BookingSlotResponse> departuresToday,
        List<BookingSlotResponse> expectedReturnsToday,
        List<BookingSlotResponse> assignedToday,
        List<TripResponse> currentTrips,
        List<VehicleSummary> availableVehicles,
        List<DriverSummary> availableDrivers) {

    public record Counts(int upcomingJobs, int unassignedJobs, int departuresToday, int expectedReturnsToday,
                         int currentTrips, int availableVehicles, int availableDrivers, int assignedVehicles, int assignedDrivers) {}
}
