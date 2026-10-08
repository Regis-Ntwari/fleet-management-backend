package com.limoz.fleet.notification.alert;

import com.limoz.fleet.document.DocumentTypeRepository;
import com.limoz.fleet.document.dto.DocumentRequest;
import com.limoz.fleet.notification.alert.dto.AlertNoteRequest;
import com.limoz.fleet.security.Roles;
import com.limoz.fleet.support.AbstractIntegrationTest;
import com.limoz.fleet.support.TestData;
import com.limoz.fleet.vehicle.VehicleStatus;
import com.limoz.fleet.vehicle.dto.VehicleResponse;
import com.limoz.fleet.vehicle.dto.VehicleStatusChangeRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AlertIT extends AbstractIntegrationTest {

    @Autowired
    TestData data;

    @Autowired
    DocumentTypeRepository documentTypeRepository;

    @Autowired
    Clock clock;

    private long addInsurance(Long vehicleId, LocalDate expiry) throws Exception {
        Long typeId = documentTypeRepository.findByCodeIgnoreCase("INSURANCE").orElseThrow().getId();
        String body = mockMvc.perform(post("/api/v1/vehicles/" + vehicleId + "/documents").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new DocumentRequest(typeId, "POL-" + System.nanoTime(), "Radiant", expiry.minusYears(1), expiry, null, null, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private void scan() throws Exception {
        mockMvc.perform(post("/api/v1/alerts/scan").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failedScanners").isEmpty());
    }

    private JsonNode findAlert(String entityType, long entityId, AlertType type) throws Exception {
        String body = mockMvc.perform(get("/api/v1/alerts").header("Authorization", adminToken)
                        .param("entityType", entityType).param("type", type.name()).param("size", "200"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode found = null;
        int matches = 0;
        for (JsonNode a : json.readTree(body).get("content")) {
            if (a.get("entityId").asLong() == entityId) {
                found = a;
                matches++;
            }
        }
        assertThat(matches).as("alerts for %s %d", entityType, entityId).isLessThanOrEqualTo(1);
        return found;
    }

    @Test
    @DisplayName("an expired required document raises one CRITICAL alert with a link, notifies management, and auto-resolves after renewal")
    void expiredDocumentLifecycle() throws Exception {
        String fleetManager = tokenFor(Roles.FLEET_MANAGER);
        VehicleResponse vehicle = data.vehicle();
        LocalDate today = LocalDate.now(clock);
        long expiredDoc = addInsurance(vehicle.id(), today.minusDays(3));

        scan();
        JsonNode alert = findAlert("VehicleDocument", expiredDoc, AlertType.DOCUMENT_EXPIRED);
        assertThat(alert).isNotNull();
        assertThat(alert.get("severity").asText()).isEqualTo("CRITICAL");
        assertThat(alert.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(alert.get("linkPath").asText()).isEqualTo("/vehicles/" + vehicle.id() + "/documents");
        assertThat(alert.get("entityReference").asText()).contains(vehicle.plateNumber());
        assertThat(alert.get("title").asText()).contains("Insurance expired");
        long alertId = alert.get("id").asLong();
        String firstDetected = alert.get("firstDetectedAt").asText();

        // management is notified in-app about the new critical alert
        mockMvc.perform(get("/api/v1/notifications").header("Authorization", fleetManager).param("size", "100"))
                .andExpect(jsonPath("$.content[?(@.type == 'DOCUMENT_EXPIRED')].entityId").value(hasItem((int) expiredDoc)));

        // a second scan refreshes the same alert instead of duplicating it
        scan();
        JsonNode refreshed = findAlert("VehicleDocument", expiredDoc, AlertType.DOCUMENT_EXPIRED);
        assertThat(refreshed.get("id").asLong()).isEqualTo(alertId);
        assertThat(refreshed.get("firstDetectedAt").asText()).isEqualTo(firstDetected);
        assertThat(refreshed.get("status").asText()).isEqualTo("ACTIVE");

        // acknowledge (audited), cannot acknowledge twice
        mockMvc.perform(post("/api/v1/alerts/" + alertId + "/acknowledge").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(new AlertNoteRequest("Renewal in progress"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.acknowledgedByName").value("System Administrator"))
                .andExpect(jsonPath("$.acknowledgedAt").exists());
        mockMvc.perform(post("/api/v1/alerts/" + alertId + "/acknowledge").header("Authorization", adminToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        mockMvc.perform(get("/api/v1/audit-logs/entity/Alert/" + alertId).header("Authorization", adminToken))
                .andExpect(jsonPath("$[0].action").value("STATUS_CHANGE"));

        // acknowledged alerts stay open across scans
        scan();
        assertThat(findAlert("VehicleDocument", expiredDoc, AlertType.DOCUMENT_EXPIRED).get("status").asText()).isEqualTo("ACKNOWLEDGED");

        // renewing the document supersedes the expired one -> the condition clears on the next scan
        addInsurance(vehicle.id(), today.plusYears(1));
        scan();
        JsonNode resolved = findAlert("VehicleDocument", expiredDoc, AlertType.DOCUMENT_EXPIRED);
        assertThat(resolved.get("status").asText()).isEqualTo("RESOLVED");
        assertThat(resolved.get("resolutionNote").asText()).isEqualTo("Condition cleared");
        assertThat(resolved.get("resolvedAt")).isNotNull();

        mockMvc.perform(get("/api/v1/alerts/summary").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byStatus.RESOLVED").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.bySeverity.CRITICAL").exists())
                .andExpect(jsonPath("$.topTypes").isArray());
    }

    @Test
    @DisplayName("an expiring (not yet expired) document is a WARNING; manual resolution is audited and re-raised while the condition persists")
    void expiringDocumentAndManualResolve() throws Exception {
        VehicleResponse vehicle = data.vehicle();
        long doc = addInsurance(vehicle.id(), LocalDate.now(clock).plusDays(5));
        scan();
        JsonNode alert = findAlert("VehicleDocument", doc, AlertType.DOCUMENT_EXPIRING);
        assertThat(alert).isNotNull();
        assertThat(alert.get("severity").asText()).isEqualTo("WARNING");
        long alertId = alert.get("id").asLong();

        mockMvc.perform(post("/api/v1/alerts/" + alertId + "/resolve").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(new AlertNoteRequest("Handled by phone"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolutionNote").value("Handled by phone"));
        mockMvc.perform(post("/api/v1/alerts/" + alertId + "/resolve").header("Authorization", adminToken))
                .andExpect(status().isUnprocessableEntity());

        scan();
        JsonNode reraised = findAlert("VehicleDocument", doc, AlertType.DOCUMENT_EXPIRING);
        assertThat(reraised.get("id").asLong()).isEqualTo(alertId);
        assertThat(reraised.get("status").asText()).isEqualTo("ACTIVE");
        // null fields are omitted from responses (non_null inclusion): the manual note was cleared on re-raise
        assertThat(reraised.get("resolutionNote")).isNull();
        assertThat(reraised.get("resolvedAt")).isNull();
    }

    @Test
    @DisplayName("an OUT_OF_SERVICE vehicle is a WARNING alert that clears when the vehicle returns; ordering puts ACTIVE and CRITICAL first")
    void outOfServiceAlertAndOrdering() throws Exception {
        VehicleResponse vehicle = data.vehicle();
        mockMvc.perform(patch("/api/v1/vehicles/" + vehicle.id() + "/status").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new VehicleStatusChangeRequest(VehicleStatus.OUT_OF_SERVICE, "Engine rebuild"))))
                .andExpect(status().isOk());
        scan();
        JsonNode alert = findAlert("Vehicle", vehicle.id(), AlertType.VEHICLE_OUT_OF_SERVICE);
        assertThat(alert).isNotNull();
        assertThat(alert.get("severity").asText()).isEqualTo("WARNING");
        assertThat(alert.get("linkPath").asText()).isEqualTo("/vehicles/" + vehicle.id());

        String body = mockMvc.perform(get("/api/v1/alerts").header("Authorization", adminToken).param("size", "200"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        int previousRank = 0;
        for (JsonNode a : json.readTree(body).get("content")) {
            int statusRank = switch (a.get("status").asText()) { case "ACTIVE" -> 0; case "ACKNOWLEDGED" -> 1; default -> 2; };
            int severityRank = switch (a.get("severity").asText()) { case "CRITICAL" -> 0; case "WARNING" -> 1; default -> 2; };
            int rank = statusRank * 10 + severityRank;
            assertThat(rank).isGreaterThanOrEqualTo(previousRank);
            previousRank = rank;
        }

        mockMvc.perform(patch("/api/v1/vehicles/" + vehicle.id() + "/status").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new VehicleStatusChangeRequest(VehicleStatus.AVAILABLE, "Repaired"))))
                .andExpect(status().isOk());
        scan();
        assertThat(findAlert("Vehicle", vehicle.id(), AlertType.VEHICLE_OUT_OF_SERVICE).get("status").asText()).isEqualTo("RESOLVED");
    }

    @Test
    @DisplayName("DASHBOARD_VIEW can read alerts; only ALERT_MANAGE can scan, acknowledge or resolve")
    void authorization() throws Exception {
        String viewer = tokenFor(Roles.VIEWER);
        mockMvc.perform(get("/api/v1/alerts").header("Authorization", viewer)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/alerts/summary").header("Authorization", viewer)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/alerts/scan").header("Authorization", viewer)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/alerts/1/acknowledge").header("Authorization", viewer)).andExpect(status().isForbidden());
    }
}
