package com.limoz.fleet.trip.dto;

import com.limoz.fleet.booking.domain.Booking;
import com.limoz.fleet.customer.domain.Customer;
import com.limoz.fleet.driver.domain.Driver;
import com.limoz.fleet.vehicle.domain.Vehicle;

import java.time.Instant;

/** Internal command used by the dispatch module to open a trip when a booking slot departs. */
public record DeploymentTripCommand(
        Vehicle vehicle,
        Driver driver,
        Customer customer,
        Booking booking,
        Long bookingSlotId,
        String origin,
        String destination,
        String purpose,
        Integer passengers,
        Instant scheduledStartAt,
        Instant scheduledEndAt,
        long startOdometerKm,
        Instant startedAt,
        String notes) {}
