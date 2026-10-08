package com.limoz.fleet.vehicle;

/** Published after a vehicle's operational status changes (committed with the owning transaction). */
public record VehicleStatusChangedEvent(Long vehicleId, String plateNumber, VehicleStatus from, VehicleStatus to, String reason) {}
