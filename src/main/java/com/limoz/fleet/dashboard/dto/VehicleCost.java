package com.limoz.fleet.dashboard.dto;

import java.math.BigDecimal;

public record VehicleCost(Long vehicleId, String plateNumber, String category, BigDecimal fuelCost, BigDecimal maintenanceCost,
                          BigDecimal expenses, BigDecimal fines, BigDecimal totalCost, BigDecimal distanceKm, BigDecimal costPerKm) {}
