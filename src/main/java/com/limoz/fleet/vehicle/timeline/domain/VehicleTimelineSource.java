package com.limoz.fleet.vehicle.timeline.domain;

import java.time.Instant;
import java.util.List;

/**
 * Modules contribute events to the vehicle activity timeline by implementing this interface
 * (trips, fuel, maintenance, incidents, deployments...). Queries must be bounded by the time range.
 */
public interface VehicleTimelineSource {

    List<TimelineEntry> entriesForVehicle(Long vehicleId, Instant from, Instant to);
}
