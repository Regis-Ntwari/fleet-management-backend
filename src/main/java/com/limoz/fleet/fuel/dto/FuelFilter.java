package com.limoz.fleet.fuel.dto;

import com.limoz.fleet.vehicle.FuelType;

import java.time.LocalDate;

/** Optional filters of the fuel log; dates are operational (Africa/Kigali) business dates, inclusive. */
public record FuelFilter(String q, Long vehicleId, Long driverId, FuelType fuelType, LocalDate from, LocalDate to,
                         Boolean anomaly, String station, Boolean archived) {}
