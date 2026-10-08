package com.limoz.fleet.vehicle.dto;

import com.limoz.fleet.vehicle.FuelType;
import com.limoz.fleet.vehicle.MaintenanceStatus;
import com.limoz.fleet.vehicle.OwnershipType;
import com.limoz.fleet.vehicle.Transmission;
import com.limoz.fleet.vehicle.VehicleStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record VehicleResponse(
        Long id,
        String plateNumber,
        String fleetNumber,
        String make,
        String model,
        Integer modelYear,
        Long categoryId,
        String categoryName,
        String bodyType,
        FuelType fuelType,
        Transmission transmission,
        String engineNumber,
        String chassisNumber,
        String color,
        long odometerKm,
        Integer seatingCapacity,
        LocalDate purchaseDate,
        BigDecimal acquisitionCost,
        OwnershipType ownershipType,
        String ownerName,
        String ownerContact,
        String ownerDriverName,
        String insuranceProvider,
        String insurancePolicyNumber,
        LocalDate insuranceExpiryDate,
        BigDecimal dayRate,
        Long currentDriverId,
        String currentDriverName,
        VehicleStatus operationalStatus,
        MaintenanceStatus maintenanceStatus,
        String department,
        String notes,
        boolean archived,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String updatedBy) {}
