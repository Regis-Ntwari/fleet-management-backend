package com.limoz.fleet.vehicle.dto;

import com.limoz.fleet.vehicle.domain.FuelType;
import com.limoz.fleet.vehicle.domain.OwnershipType;
import com.limoz.fleet.vehicle.domain.Transmission;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record VehicleRequest(
        @NotBlank(message = "Plate number is required") @Size(max = 20) String plateNumber,
        @Size(max = 20) String fleetNumber,
        @NotBlank @Size(max = 60) String make,
        @NotBlank @Size(max = 80) String model,
        @Min(1950) @Max(2100) Integer modelYear,
        @NotNull(message = "Vehicle category is required") Long categoryId,
        @Size(max = 40) String bodyType,
        @NotNull FuelType fuelType,
        Transmission transmission,
        @Size(max = 60) String engineNumber,
        @Size(max = 60) String chassisNumber,
        @Size(max = 40) String color,
        @Min(0) Long odometerKm,
        @Min(0) Integer seatingCapacity,
        LocalDate purchaseDate,
        @DecimalMin("0") BigDecimal acquisitionCost,
        OwnershipType ownershipType,
        @Size(max = 150) String ownerName,
        @Size(max = 60) String ownerContact,
        @Size(max = 120) String ownerDriverName,
        @Size(max = 120) String insuranceProvider,
        @Size(max = 80) String insurancePolicyNumber,
        LocalDate insuranceExpiryDate,
        @DecimalMin("0") BigDecimal dayRate,
        @Size(max = 80) String department,
        String notes) {}
