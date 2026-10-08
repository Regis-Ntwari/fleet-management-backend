package com.limoz.fleet.dashboard.controller;

import com.limoz.fleet.dashboard.service.DashboardService;

import com.limoz.fleet.dashboard.dto.AlertDigest;
import com.limoz.fleet.dashboard.dto.DailyPoint;
import com.limoz.fleet.dashboard.dto.DashboardSummary;
import com.limoz.fleet.dashboard.dto.DeploymentToday;
import com.limoz.fleet.dashboard.dto.FleetStatusResponse;
import com.limoz.fleet.dashboard.dto.FuelTrendResponse;
import com.limoz.fleet.dashboard.dto.MaintenanceDashboardResponse;
import com.limoz.fleet.dashboard.dto.RecentBooking;
import com.limoz.fleet.dashboard.dto.UtilizationResponse;
import com.limoz.fleet.dashboard.dto.VehicleCost;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Executive KPIs and trends computed from the database (cached 60 s)")
@PreAuthorize("hasAuthority('DASHBOARD_VIEW')")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    @Operation(summary = "KPI cards for a day (default today): fleet position, trips, distance, drivers, maintenance, fuel, bookings, incidents, documents, finance, alerts, utilisation")
    public DashboardSummary summary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                    @RequestParam(required = false) Long categoryId,
                                    @RequestParam(required = false) String department) {
        return dashboardService.summary(date, categoryId, department);
    }

    @GetMapping("/fleet-status")
    @Operation(summary = "Fleet status distribution (by operational status, category and maintenance status)")
    public FleetStatusResponse fleetStatus() {
        return dashboardService.fleetStatus();
    }

    @GetMapping("/utilization")
    @Operation(summary = "Fleet utilisation: vehicles used per day and per-vehicle classification (HEAVY / NORMAL / UNDER_UTILISED)")
    public UtilizationResponse utilization(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                           @RequestParam(required = false) Long categoryId) {
        return dashboardService.utilization(from, to, categoryId);
    }

    @GetMapping("/fuel-trend")
    @Operation(summary = "Fuel consumption trend, totals, average L/100 km, anomalies and top consumers")
    public FuelTrendResponse fuelTrend(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                       @RequestParam(required = false) Long vehicleId,
                                       @RequestParam(required = false) Long categoryId) {
        return dashboardService.fuelTrend(from, to, vehicleId, categoryId);
    }

    @GetMapping("/maintenance")
    @Operation(summary = "Maintenance trend: open jobs, workshop load, cost per month, due/overdue schedules, top vehicles by cost")
    public MaintenanceDashboardResponse maintenance(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return dashboardService.maintenance(from, to);
    }

    @GetMapping("/alerts")
    @Operation(summary = "Alert centre digest: counts by severity and the top active alerts with links")
    public AlertDigest alerts() {
        return dashboardService.alerts();
    }

    @GetMapping("/trips-per-day")
    public List<DailyPoint> tripsPerDay(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                        @RequestParam(required = false) Long vehicleId,
                                        @RequestParam(required = false) Long driverId) {
        return dashboardService.tripsPerDay(from, to, vehicleId, driverId);
    }

    @GetMapping("/distance-trend")
    @Operation(summary = "Daily distance (from telematics summaries when available, otherwise completed trips)")
    public List<DailyPoint> distanceTrend(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                          @RequestParam(required = false) Long vehicleId,
                                          @RequestParam(required = false) Long categoryId) {
        return dashboardService.distanceTrend(from, to, vehicleId, categoryId);
    }

    @GetMapping("/cost-by-vehicle")
    @Operation(summary = "Operational cost per vehicle (fuel + maintenance + expenses + fines) and cost per km")
    public List<VehicleCost> costByVehicle(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                           @RequestParam(required = false) Long categoryId) {
        return dashboardService.costByVehicle(from, to, categoryId);
    }

    @GetMapping("/availability-trend")
    @Operation(summary = "Vehicles available per day (fleet minus workshop minus deployed)")
    public List<DailyPoint> availabilityTrend(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return dashboardService.availabilityTrend(from, to);
    }

    @GetMapping("/deployments-today")
    @Operation(summary = "Today's deployments panel")
    public List<DeploymentToday> deploymentsToday(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return dashboardService.todaysDeployments(date);
    }

    @GetMapping("/recent-bookings")
    @Operation(summary = "Recent bookings panel")
    public List<RecentBooking> recentBookings() {
        return dashboardService.recentBookings();
    }
}
