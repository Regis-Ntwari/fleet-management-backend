package com.limoz.fleet.vehicle.dto;

import com.limoz.fleet.vehicle.FuelType;
import com.limoz.fleet.vehicle.MaintenanceStatus;
import com.limoz.fleet.vehicle.OwnershipType;
import com.limoz.fleet.vehicle.VehicleStatus;

import java.util.List;

public record VehicleFilter(String q, Long categoryId, List<VehicleStatus> status, MaintenanceStatus maintenanceStatus,
                            FuelType fuelType, OwnershipType ownershipType, String department, Long driverId, Boolean archived) {}
