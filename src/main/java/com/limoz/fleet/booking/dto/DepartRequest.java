package com.limoz.fleet.booking.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Departure of an assigned slot. The odometer defaults to the vehicle's current reading and the
 * departure time to now (a past time may be given when the departure is recorded after the fact).
 */
public record DepartRequest(
        @Min(0) Long odometerOut,
        Instant departedAt,
        @Size(max = 255) String destination,
        @Size(max = 255) String notes) {}
