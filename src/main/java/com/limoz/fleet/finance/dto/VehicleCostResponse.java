package com.limoz.fleet.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** "Costs" tab of the vehicle profile: fuel, maintenance, expenses and fines over a period, with cost per km. */
public record VehicleCostResponse(
        Long vehicleId,
        String plateNumber,
        LocalDate from,
        LocalDate to,
        String currency,
        BigDecimal fuelCost,
        BigDecimal maintenanceCost,
        BigDecimal expenses,
        BigDecimal fines,
        BigDecimal totalCost,
        BigDecimal distanceKm,
        BigDecimal costPerKm) {}
