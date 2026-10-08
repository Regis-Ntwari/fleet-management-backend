package com.limoz.fleet.incident;

import com.limoz.fleet.incident.dto.IncidentNoteRequest;
import com.limoz.fleet.incident.dto.IncidentRequest;
import com.limoz.fleet.incident.dto.IncidentResolveRequest;
import com.limoz.fleet.security.Roles;
import com.limoz.fleet.support.AbstractIntegrationTest;
import com.limoz.fleet.support.TestData;
import com.limoz.fleet.vehicle.dto.VehicleResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IncidentIT extends AbstractIntegrationTest {

    @Autowired
    TestData data;

    private IncidentRequest request(Long vehicleId, IncidentType type, IncidentSeverity severity, Instant occurredAt) {
        return new IncidentRequest(vehicleId, null, null, occurredAt, "Nyabugogo roundabout", null, null, type, severity,
                "Rear bumper damaged while reversing", false, false, null, null, new BigDecimal("150000"), null);
    }

    private long report(Long vehicleId, IncidentType type, IncidentSeverity severity) throws Exception {
        String body = mockMvc.perform(post("/api/v1/incidents").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request(vehicleId, type, severity, Instant.now().minus(2, ChronoUnit.HOURS)))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    @Test
    @DisplayName("reporting an incident numbers it, defaults the driver and writes the first history entry")
    void reportWritesHistory() throws Exception {
        VehicleResponse vehicle = data.vehicle();
        mockMvc.perform(post("/api/v1/incidents").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request(vehicle.id(), IncidentType.DAMAGE, IncidentSeverity.MINOR, Instant.now().minus(1, ChronoUnit.HOURS)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.incidentNumber").value(startsWith("INC-")))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.vehicle.plateNumber").value(vehicle.plateNumber()))
                .andExpect(jsonPath("$.reportedByUserId").exists())
                .andExpect(jsonPath("$.updates.length()").value(1))
                .andExpect(jsonPath("$.updates[0].updateType").value("NOTE"))
                .andExpect(jsonPath("$.updates[0].authorName").exists());

        mockMvc.perform(get("/api/v1/vehicles/" + vehicle.id()).header("Authorization", adminToken))
                .andExpect(jsonPath("$.operationalStatus").value("AVAILABLE"));
        mockMvc.perform(get("/api/v1/vehicles/" + vehicle.id() + "/incidents").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("investigate -> resolve -> close -> reopen keeps the full status history")
    void lifecycle() throws Exception {
        long id = report(data.vehicle().id(), IncidentType.BREAKDOWN, IncidentSeverity.MODERATE);

        mockMvc.perform(post("/api/v1/incidents/" + id + "/investigate").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(new IncidentNoteRequest("Mechanic dispatched"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_INVESTIGATION"));

        mockMvc.perform(post("/api/v1/incidents/" + id + "/resolve").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(new IncidentResolveRequest("Alternator replaced", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.correctiveAction").value("Alternator replaced"))
                .andExpect(jsonPath("$.resolvedAt").exists());

        mockMvc.perform(post("/api/v1/incidents/" + id + "/close").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.closedAt").exists());

        mockMvc.perform(post("/api/v1/incidents/" + id + "/reopen").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(new IncidentNoteRequest("Fault reappeared"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_INVESTIGATION"))
                .andExpect(jsonPath("$.closedAt").doesNotExist());

        mockMvc.perform(get("/api/v1/incidents/" + id + "/updates").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].updateType").value("NOTE"))
                .andExpect(jsonPath("$[1].updateType").value("STATUS_CHANGE"))
                .andExpect(jsonPath("$[1].fromStatus").value("OPEN"))
                .andExpect(jsonPath("$[1].toStatus").value("UNDER_INVESTIGATION"))
                .andExpect(jsonPath("$[4].toStatus").value("UNDER_INVESTIGATION"))
                .andExpect(jsonPath("$[4].note").value("Reopened: Fault reappeared"));

        mockMvc.perform(get("/api/v1/audit-logs/entity/Incident/" + id).header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(5)));
    }

    @Test
    @DisplayName("illegal transitions are rejected with 422 and the resolution needs a corrective action")
    void invalidTransitions() throws Exception {
        long id = report(data.vehicle().id(), IncidentType.THEFT, IncidentSeverity.MINOR);
        mockMvc.perform(post("/api/v1/incidents/" + id + "/close").header("Authorization", adminToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        mockMvc.perform(post("/api/v1/incidents/" + id + "/reopen").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(new IncidentNoteRequest("x"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        mockMvc.perform(post("/api/v1/incidents/" + id + "/resolve").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"correctiveAction\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("a MAJOR accident takes the vehicle out of service")
    void majorAccidentGroundsVehicle() throws Exception {
        VehicleResponse vehicle = data.vehicle();
        report(vehicle.id(), IncidentType.ACCIDENT, IncidentSeverity.MAJOR);
        mockMvc.perform(get("/api/v1/vehicles/" + vehicle.id()).header("Authorization", adminToken))
                .andExpect(jsonPath("$.operationalStatus").value("OUT_OF_SERVICE"));
        mockMvc.perform(get("/api/v1/incidents/summary").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byType.ACCIDENT").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.topVehicles[0].count").value(greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("incidents cannot occur in the future and viewers cannot report them")
    void validationAndAuthorization() throws Exception {
        VehicleResponse vehicle = data.vehicle();
        mockMvc.perform(post("/api/v1/incidents").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request(vehicle.id(), IncidentType.OTHER, IncidentSeverity.MINOR, Instant.now().plus(1, ChronoUnit.DAYS)))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("OCCURRED_IN_FUTURE"));

        String viewer = tokenFor(Roles.VIEWER);
        mockMvc.perform(get("/api/v1/incidents").header("Authorization", viewer).param("q", "nyabugogo").param("status", "OPEN"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/incidents").header("Authorization", viewer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request(vehicle.id(), IncidentType.OTHER, IncidentSeverity.MINOR, Instant.now()))))
                .andExpect(status().isForbidden());
    }
}
