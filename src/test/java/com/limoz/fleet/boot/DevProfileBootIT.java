package com.limoz.fleet.boot;

import com.limoz.fleet.support.EmbeddedPostgresExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots the application exactly like a fresh developer installation: dev profile, empty database,
 * Flyway migrations, bootstrap administrator and the full seed dataset on start-up.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class DevProfileBootIT {

    @Autowired
    MockMvc mockMvc;

    @DynamicPropertySource
    static void freshDatabase(DynamicPropertyRegistry registry) throws Exception {
        try (Connection c = DriverManager.getConnection(EmbeddedPostgresExtension.jdbcUrl(), "postgres", "postgres");
             Statement st = c.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS limoz_dev_boot");
            st.execute("CREATE DATABASE limoz_dev_boot");
        }
        registry.add("spring.datasource.url", () -> EmbeddedPostgresExtension.get().getJdbcUrl("postgres", "limoz_dev_boot"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("fleet.storage.local.base-path", () -> System.getProperty("java.io.tmpdir") + "/limoz-fleet-boot-uploads");
        registry.add("spring.task.scheduling.enabled", () -> "false");
    }

    @Test
    @DisplayName("fresh dev installation: migrations, bootstrap admin login, seed data and dashboard all work")
    void freshInstallationIsUsable() throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@limoz.rw\",\"password\":\"Admin@12345\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = "Bearer " + body.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
        mockMvc.perform(get("/api/v1/dashboard/summary").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalVehicles").value(25))
                .andExpect(jsonPath("$.totalDrivers").value(14))
                .andExpect(jsonPath("$.activeAlerts").value(greaterThanOrEqualTo(5)));
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"fleet.manager@limoz.rw\",\"password\":\"Limoz@2026\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.roles[0]").value("FLEET_MANAGER"));
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
