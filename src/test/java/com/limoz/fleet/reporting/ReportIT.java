package com.limoz.fleet.reporting;

import com.limoz.fleet.security.Roles;
import com.limoz.fleet.support.AbstractIntegrationTest;
import com.limoz.fleet.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReportIT extends AbstractIntegrationTest {

    @Autowired
    TestData data;

    @Test
    @DisplayName("reports are discoverable, run as JSON and export as Excel/PDF/CSV")
    void reportsRunAndExport() throws Exception {
        data.vehicle();
        mockMvc.perform(get("/api/v1/reports").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", hasItem("daily-fleet")))
                .andExpect(jsonPath("$[*].code", hasItem("fleet-availability")));
        mockMvc.perform(get("/api/v1/reports/daily-fleet").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Daily Fleet Report"))
                .andExpect(jsonPath("$.columns.length()").value(8))
                .andExpect(jsonPath("$.rows.length()").value(greaterThanOrEqualTo(1)));
        mockMvc.perform(get("/api/v1/reports/daily-fleet").param("format", "xlsx").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("daily-fleet-")))
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        mockMvc.perform(get("/api/v1/reports/fleet-availability").param("format", "pdf").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"));
        mockMvc.perform(get("/api/v1/reports/unknown-report").header("Authorization", adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a VIEWER can view reports but cannot export them")
    void viewerCannotExport() throws Exception {
        String viewer = tokenFor(Roles.VIEWER);
        mockMvc.perform(get("/api/v1/reports/daily-fleet").header("Authorization", viewer))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/reports/daily-fleet").param("format", "csv").header("Authorization", viewer))
                .andExpect(status().isForbidden());
    }
}
