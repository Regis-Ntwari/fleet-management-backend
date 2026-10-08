package com.limoz.fleet.dashboard;

import com.limoz.fleet.assignment.AssignmentService;
import com.limoz.fleet.assignment.dto.AssignmentRequest;
import com.limoz.fleet.security.Roles;
import com.limoz.fleet.support.AbstractIntegrationTest;
import com.limoz.fleet.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DashboardIT extends AbstractIntegrationTest {

    @Autowired
    TestData data;

    @Autowired
    AssignmentService assignmentService;

    @Test
    @DisplayName("summary KPIs reflect database records and every dashboard endpoint responds")
    void dashboardEndpoints() throws Exception {
        var vehicle = data.vehicle();
        var driver = data.driver();
        assignmentService.assign(new AssignmentRequest(vehicle.id(), driver.id(), null, "ops", null, null));

        mockMvc.perform(get("/api/v1/dashboard/summary").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalVehicles").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.assignedVehicles").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.activeDrivers").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.utilisationPercent").exists())
                .andExpect(jsonPath("$.fuelCostMonth").exists());
        mockMvc.perform(get("/api/v1/dashboard/fleet-status").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byStatus[?(@.label=='ASSIGNED')].count").value(org.hamcrest.Matchers.hasItem(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.byCategory[?(@.label=='Minibus')]").exists());
        for (String path : new String[]{"/utilization", "/fuel-trend", "/maintenance", "/alerts", "/trips-per-day", "/distance-trend",
                "/cost-by-vehicle", "/availability-trend", "/deployments-today", "/recent-bookings"}) {
            mockMvc.perform(get("/api/v1/dashboard" + path).header("Authorization", adminToken)).andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/v1/dashboard/utilization").header("Authorization", adminToken).param("from", "2026-06-01").param("to", "2026-06-07"))
                .andExpect(jsonPath("$.daily.length()").value(7))
                .andExpect(jsonPath("$.vehicles[0].classification").exists());
        mockMvc.perform(get("/api/v1/dashboard/cost-by-vehicle").header("Authorization", adminToken))
                .andExpect(jsonPath("$[0].totalCost").exists());
    }

    @Test
    @DisplayName("dashboard requires DASHBOARD_VIEW")
    void requiresPermission() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary").header("Authorization", tokenFor(Roles.TECHNICIAN)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/dashboard/summary").header("Authorization", tokenFor(Roles.MANAGEMENT)))
                .andExpect(status().isOk());
    }
}
