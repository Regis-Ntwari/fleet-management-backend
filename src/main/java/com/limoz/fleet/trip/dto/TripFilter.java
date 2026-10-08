package com.limoz.fleet.trip.dto;

import com.limoz.fleet.trip.TripStatus;

import java.time.LocalDate;
import java.util.List;

/** Trip search filters; {@code from}/{@code to} are operational dates applied to the scheduled start. */
public record TripFilter(String q, Long vehicleId, Long driverId, Long customerId, Long bookingId,
                         List<TripStatus> status, LocalDate from, LocalDate to) {}
