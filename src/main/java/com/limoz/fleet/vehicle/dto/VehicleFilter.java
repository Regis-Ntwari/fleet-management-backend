package com.limoz.fleet.vehicle.dto;

import com.limoz.fleet.vehicle.domain.FuelType;
import com.limoz.fleet.vehicle.domain.MaintenanceStatus;
import com.limoz.fleet.vehicle.domain.OwnershipType;
import com.limoz.fleet.vehicle.domain.VehicleStatus;

import java.util.List;

public record VehicleFilter(String q, Long categoryId, List<VehicleStatus> status, MaintenanceStatus maintenanceStatus,
                            FuelType fuelType, OwnershipType ownershipType, String department, Long driverId, Boolean archived) {}
