package com.limoz.fleet.trip.dto;

import jakarta.validation.constraints.Min;

import java.time.Instant;

/** Start a trip; the odometer defaults to the vehicle's current reading and the start time to now. */
public record TripStartRequest(@Min(0) Long startOdometerKm, Instant startedAt) {}
