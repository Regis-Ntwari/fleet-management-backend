package com.limoz.fleet.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Executive KPIs for one operational day (all values computed from the database). */
public record DashboardSummary(
        LocalDate date,
        long totalVehicles,
        long availableVehicles,
        long assignedVehicles,
        long onTripVehicles,
        long reservedVehicles,
        long inWorkshopVehicles,
        long outOfServiceVehicles,
        long idleVehicles,
        long vehiclesWithGpsProblems,
        long vehiclesWithFuelSensorProblems,
        long tripsToday,
        BigDecimal distanceTodayKm,
        long totalDrivers,
        long activeDrivers,
        long availableDrivers,
        long vehiclesDueForService,
        long overdueMaintenance,
        long openMaintenanceJobs,
        BigDecimal fuelConsumedMonthLitres,
        BigDecimal fuelCostMonth,
        long upcomingBookings,
        long bookingsAwaitingDispatch,
        long activeIncidents,
        long expiredDocuments,
        long expiringDocuments,
        long unpaidFines,
        long overdueInvoices,
        BigDecimal outstandingReceivables,
        long activeAlerts,
        long criticalAlerts,
        BigDecimal utilisationPercent) {}
