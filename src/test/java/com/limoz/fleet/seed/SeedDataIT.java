package com.limoz.fleet.seed;

import com.limoz.fleet.seed.domain.SeedContext;
import com.limoz.fleet.seed.domain.SeedStep;

import com.limoz.fleet.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs every seed step against the real services (the same path a fresh dev installation takes) and checks
 * that the resulting dataset makes the dashboard, reports and lists meaningful.
 */
class SeedDataIT extends AbstractIntegrationTest {

    @Autowired
    List<SeedStep> steps;

    @Autowired
    com.limoz.fleet.seed.service.SeedSecurity seedSecurity;

    @Autowired
    Clock clock;

    @Autowired
    ZoneId zone;

    @Test
    @DisplayName("seed steps populate every module and the dashboard reflects the data")
    void seedProducesUsableDataset() throws Exception {
        SeedContext context = new SeedContext(clock, zone);
        seedSecurity.runAsAdministrator(() -> steps.stream().sorted(Comparator.comparingInt(SeedStep::order)).forEach(step -> step.seed(context)));

        mockMvc.perform(get("/api/v1/dashboard/summary").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalVehicles").value(greaterThanOrEqualTo(25)))
                .andExpect(jsonPath("$.totalDrivers").value(greaterThanOrEqualTo(14)))
                .andExpect(jsonPath("$.assignedVehicles").value(greaterThanOrEqualTo(5)))
                .andExpect(jsonPath("$.openMaintenanceJobs").value(greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.expiredDocuments").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.upcomingBookings").value(greaterThanOrEqualTo(1)));
        mockMvc.perform(get("/api/v1/dashboard/fuel-trend").header("Authorization", adminToken).param("from", context.today().minusDays(60).toString()))
                .andExpect(jsonPath("$.totalLitres").value(greaterThanOrEqualTo(100.0)))
                .andExpect(jsonPath("$.anomalies").value(greaterThanOrEqualTo(1)));
        mockMvc.perform(get("/api/v1/dashboard/maintenance").header("Authorization", adminToken))
                .andExpect(jsonPath("$.completedInRange").value(greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.overdue").value(greaterThanOrEqualTo(1)));
        mockMvc.perform(get("/api/v1/trips").header("Authorization", adminToken).param("status", "COMPLETED"))
                .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(20)));
        mockMvc.perform(get("/api/v1/vouchers").header("Authorization", adminToken))
                .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(10)));
        mockMvc.perform(get("/api/v1/spare-parts/low-stock").header("Authorization", adminToken))
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(1)));
        mockMvc.perform(get("/api/v1/reports/daily-fleet").header("Authorization", adminToken))
                .andExpect(jsonPath("$.rows.length()").value(greaterThanOrEqualTo(25)));
        // compliance, finance and telematics data
        mockMvc.perform(get("/api/v1/incidents").header("Authorization", adminToken))
                .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(7)));
        mockMvc.perform(get("/api/v1/invoices").header("Authorization", adminToken))
                .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(3)));
        mockMvc.perform(get("/api/v1/commitments").header("Authorization", adminToken))
                .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(7)));
        mockMvc.perform(get("/api/v1/telematics/latest").header("Authorization", adminToken))
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(10)));
        mockMvc.perform(get("/api/v1/movement/summary").header("Authorization", adminToken).param("date", context.today().minusDays(1).toString()))
                .andExpect(jsonPath("$.vehiclesMoved").value(greaterThanOrEqualTo(5)));
        // alert centre populated by the scanners (expired documents, GPS, maintenance, fines, invoices, stock...)
        mockMvc.perform(get("/api/v1/alerts/summary").header("Authorization", adminToken))
                .andExpect(jsonPath("$.open").value(greaterThanOrEqualTo(10)));
        mockMvc.perform(get("/api/v1/alerts").header("Authorization", adminToken).param("type", "MAINTENANCE_OVERDUE"))
                .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(1)));
        mockMvc.perform(get("/api/v1/alerts").header("Authorization", adminToken).param("type", "FINE_UNPAID"))
                .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(1)));
        // every report runs on the seeded data
        for (String code : new String[]{"maintenance", "maintenance-cost", "fuel-consumption", "fuel-variance", "vehicle-cost", "incidents",
                "upcoming-service", "technical", "fuel-gsl", "trips", "deployment", "driver-utilization", "vehicle-movement", "expired-documents", "fleet-availability", "driver-roster"}) {
            mockMvc.perform(get("/api/v1/reports/" + code).header("Authorization", adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rows.length()").value(greaterThanOrEqualTo(1)));
        }
        mockMvc.perform(get("/api/v1/reports/technical").param("format", "pdf").header("Authorization", adminToken)).andExpect(status().isOk());
        // global search reaches every module
        mockMvc.perform(get("/api/v1/search").header("Authorization", adminToken).param("q", "MNT-"))
                .andExpect(jsonPath("$.groups.maintenance").isNotEmpty());
        mockMvc.perform(get("/api/v1/search").header("Authorization", adminToken).param("q", "INC-"))
                .andExpect(jsonPath("$.groups.incidents").isNotEmpty());
        mockMvc.perform(get("/api/v1/vehicles/" + context.id("vehicle:RAD 855 G") + "/timeline").header("Authorization", adminToken)
                        .param("from", context.today().minusDays(60).atStartOfDay(zone).toInstant().toString()))
                .andExpect(jsonPath("$[?(@.category=='MAINTENANCE')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.category=='FUEL')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.category=='INCIDENT')]").isNotEmpty());
        // seed accounts can log in
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + login("dispatcher@limoz.rw", SeedContext.PASSWORD).accessToken()))
                .andExpect(jsonPath("$.roles[0]").value("DISPATCHER"));
    }
}
