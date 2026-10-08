package com.limoz.fleet.vehicle.dto;

import com.limoz.fleet.vehicle.VehicleStatus;

/** Compact vehicle reference embedded in other modules' responses. */
public record VehicleSummary(Long id, String plateNumber, String fleetNumber, String make, String model, String categoryName,
                             VehicleStatus operationalStatus) {}
