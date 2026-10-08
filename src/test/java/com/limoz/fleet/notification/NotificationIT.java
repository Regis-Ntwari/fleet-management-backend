package com.limoz.fleet.notification;

import com.limoz.fleet.common.event.OperationalEvent;
import com.limoz.fleet.common.event.Severity;
import com.limoz.fleet.security.Roles;
import com.limoz.fleet.support.AbstractIntegrationTest;
import com.limoz.fleet.support.TestData;
import com.limoz.fleet.vehicle.domain.VehicleStatus;
import com.limoz.fleet.vehicle.dto.VehicleResponse;
import com.limoz.fleet.vehicle.dto.VehicleStatusChangeRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationIT extends AbstractIntegrationTest {

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    TestData data;

    private void publishInTransaction(OperationalEvent event) {
        transactionTemplate.executeWithoutResult(status -> events.publishEvent(event));
    }

    private long unreadCount(String token) throws Exception {
        String body = mockMvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("unread").asLong();
    }

    private JsonNode findByTitle(String token, String title) throws Exception {
        String body = mockMvc.perform(get("/api/v1/notifications").header("Authorization", token).param("size", "100"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (JsonNode n : json.readTree(body).get("content")) {
            if (title.equals(n.get("title").asText())) {
                return n;
            }
        }
        return null;
    }

    private long countByTitle(String token, String title) throws Exception {
        String body = mockMvc.perform(get("/api/v1/notifications").header("Authorization", token).param("size", "100"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long count = 0;
        for (JsonNode n : json.readTree(body).get("content")) {
            if (title.equals(n.get("title").asText())) count++;
        }
        return count;
    }

    @Test
    @DisplayName("an OperationalEvent published inside a transaction becomes a notification for users of the target roles, once per day")
    void eventCreatesNotifications() throws Exception {
        String fleetManager = tokenFor(Roles.FLEET_MANAGER);
        String dispatcher = tokenFor(Roles.DISPATCHER);
        long unreadBefore = unreadCount(fleetManager);
        String title = "Incident " + UUID.randomUUID();
        long entityId = System.nanoTime() % 1_000_000;
        OperationalEvent event = OperationalEvent.of("INCIDENT_CREATED", Severity.CRITICAL, title, "Collision reported on the Huye road",
                "Incident", entityId, "INC-1", "/incidents/" + entityId, Roles.FLEET_MANAGER);

        publishInTransaction(event);
        publishInTransaction(event); // same event, same day -> deduplicated

        JsonNode notification = findByTitle(fleetManager, title);
        assertThat(notification).isNotNull();
        assertThat(notification.get("severity").asText()).isEqualTo("CRITICAL");
        assertThat(notification.get("type").asText()).isEqualTo("INCIDENT_CREATED");
        assertThat(notification.get("linkPath").asText()).isEqualTo("/incidents/" + entityId);
        assertThat(notification.get("read").asBoolean()).isFalse();
        assertThat(countByTitle(fleetManager, title)).isEqualTo(1);
        assertThat(unreadCount(fleetManager)).isEqualTo(unreadBefore + 1);
        // the dispatcher does not hold a target role
        assertThat(findByTitle(dispatcher, title)).isNull();

        mockMvc.perform(get("/api/v1/notifications").header("Authorization", fleetManager).param("unreadOnly", "true").param("size", "100"))
                .andExpect(jsonPath("$.content[*].title").value(hasItem(title)));
    }

    @Test
    @DisplayName("notifications can be marked read, all read, and deleted - by their recipient only")
    void readAndOwnership() throws Exception {
        String fleetManager = tokenFor(Roles.FLEET_MANAGER);
        String dispatcher = tokenFor(Roles.DISPATCHER);
        String title = "Booking " + UUID.randomUUID();
        long entityId = System.nanoTime() % 1_000_000;
        publishInTransaction(OperationalEvent.of("BOOKING_ASSIGNED", Severity.INFO, title, "Booking assigned to you",
                "Booking", entityId, "BK-1", "/bookings/" + entityId, Roles.DISPATCHER));
        JsonNode notification = findByTitle(dispatcher, title);
        assertThat(notification).isNotNull();
        long id = notification.get("id").asLong();

        // another user cannot read, mark or delete it (404 so ids cannot be probed)
        mockMvc.perform(post("/api/v1/notifications/" + id + "/read").header("Authorization", fleetManager))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/notifications/" + id).header("Authorization", fleetManager))
                .andExpect(status().isNotFound());
        assertThat(findByTitle(fleetManager, title)).isNull();

        long unreadBefore = unreadCount(dispatcher);
        mockMvc.perform(post("/api/v1/notifications/" + id + "/read").header("Authorization", dispatcher))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true))
                .andExpect(jsonPath("$.readAt").exists());
        assertThat(unreadCount(dispatcher)).isEqualTo(unreadBefore - 1);

        mockMvc.perform(post("/api/v1/notifications/read-all").header("Authorization", dispatcher))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marked").value(greaterThanOrEqualTo(0)));
        assertThat(unreadCount(dispatcher)).isZero();

        mockMvc.perform(delete("/api/v1/notifications/" + id).header("Authorization", dispatcher))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/notifications").header("Authorization", dispatcher).param("size", "100"))
                .andExpect(jsonPath("$.content[*].title").value(not(hasItem(title))));
    }

    @Test
    @DisplayName("a vehicle going OUT_OF_SERVICE notifies dispatchers and fleet managers")
    void vehicleUnavailableNotification() throws Exception {
        String dispatcher = tokenFor(Roles.DISPATCHER);
        VehicleResponse vehicle = data.vehicle();
        mockMvc.perform(patch("/api/v1/vehicles/" + vehicle.id() + "/status").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new VehicleStatusChangeRequest(VehicleStatus.OUT_OF_SERVICE, "Gearbox failure"))))
                .andExpect(status().isOk());

        JsonNode notification = findByTitle(dispatcher, "Vehicle unavailable: " + vehicle.plateNumber());
        assertThat(notification).isNotNull();
        assertThat(notification.get("type").asText()).isEqualTo("VEHICLE_UNAVAILABLE");
        assertThat(notification.get("severity").asText()).isEqualTo("INFO");
        assertThat(notification.get("message").asText()).contains("Gearbox failure");
        assertThat(notification.get("linkPath").asText()).isEqualTo("/vehicles/" + vehicle.id());
    }
}
