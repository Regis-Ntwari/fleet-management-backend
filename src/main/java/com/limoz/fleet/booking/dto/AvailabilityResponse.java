package com.limoz.fleet.booking.dto;

import com.limoz.fleet.vehicle.domain.VehicleStatus;
import com.limoz.fleet.vehicle.dto.VehicleSummary;

import java.time.LocalDate;
import java.util.List;

/** Availability calendar: per vehicle, the spans in [from, to] during which it is not free. */
public record AvailabilityResponse(LocalDate from, LocalDate to, int vehicles, List<VehicleAvailability> rows) {

    public record VehicleAvailability(VehicleSummary vehicle, Long categoryId, VehicleStatus operationalStatus,
                                      boolean fullyAvailable, List<UnavailabilitySpan> spans) {}

    /** A closed date range during which the vehicle is unavailable, with a display reason such as "Deployed · MTN Rwanda". */
    public record UnavailabilitySpan(LocalDate from, LocalDate to, UnavailabilityType type, String reason,
                                     String referenceType, Long referenceId, String reference) {}

    public enum UnavailabilityType { RESERVED, DEPLOYED, TRIP, MAINTENANCE, OUT_OF_SERVICE }
}
