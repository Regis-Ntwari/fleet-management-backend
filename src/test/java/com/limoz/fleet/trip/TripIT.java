package com.limoz.fleet.trip;

import com.limoz.fleet.booking.BookingTestData;
import com.limoz.fleet.driver.dto.DriverResponse;
import com.limoz.fleet.security.Roles;
import com.limoz.fleet.support.AbstractIntegrationTest;
import com.limoz.fleet.trip.dto.TripCancelRequest;
import com.limoz.fleet.trip.dto.TripCompleteRequest;
import com.limoz.fleet.trip.dto.TripRequest;
import com.limoz.fleet.trip.dto.TripStartRequest;
import com.limoz.fleet.user.dto.CreateUserRequest;
import com.limoz.fleet.vehicle.dto.VehicleResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TripIT extends AbstractIntegrationTest {

    @Autowired
    BookingTestData data;

    private final Instant tomorrow8 = Instant.now().truncatedTo(ChronoUnit.HOURS).plus(Duration.ofDays(1));

    @Test
    @DisplayName("a trip is planned, started with an odometer reading and completed with distance and duration")
    void planStartComplete() throws Exception {
        VehicleResponse vehicle = data.compliantVehicle();
        DriverResponse driver = data.data().driver();
        long tripId = create(request(vehicle.id(), driver.id(), tomorrow8, tomorrow8.plus(Duration.ofHours(4))));

        mockMvc.perform(post("/api/v1/trips/" + tripId + "/dispatch").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPATCHED"));

        // the odometer cannot go backwards (vehicle is at 15 000 km)
        mockMvc.perform(post("/api/v1/trips/" + tripId + "/start").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(new TripStartRequest(14000L, null))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ODOMETER_DECREASE"));

        Instant startedAt = Instant.now().minus(Duration.ofHours(3)).truncatedTo(ChronoUnit.SECONDS);
        mockMvc.perform(post("/api/v1/trips/" + tripId + "/start").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(new TripStartRequest(15020L, startedAt))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.startOdometerKm").value(15020));
        mockMvc.perform(get("/api/v1/vehicles/" + vehicle.id()).header("Authorization", adminToken))
                .andExpect(jsonPath("$.operationalStatus").value("ON_TRIP"))
                .andExpect(jsonPath("$.odometerKm").value(15020));
        mockMvc.perform(get("/api/v1/drivers/" + driver.id()).header("Authorization", adminToken))
                .andExpect(jsonPath("$.status").value("ON_TRIP"));
        mockMvc.perform(get("/api/v1/trips/active").header("Authorization", adminToken))
                .andExpect(jsonPath("$[?(@.id == " + tripId + ")].status").value("IN_PROGRESS"));

        mockMvc.perform(post("/api/v1/trips/" + tripId + "/complete").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new TripCompleteRequest(15010L, null, null, null, null))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("END_ODOMETER_BELOW_START"));

        Instant endedAt = startedAt.plus(Duration.ofMinutes(150));
        mockMvc.perform(post("/api/v1/trips/" + tripId + "/complete").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new TripCompleteRequest(15270L, endedAt, new BigDecimal("22.5"), new BigDecimal("95"), "Smooth"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.distanceKm").value(250.0))
                .andExpect(jsonPath("$.durationMinutes").value(150))
                .andExpect(jsonPath("$.fuelUsedLitres").value(22.5))
                .andExpect(jsonPath("$.maxSpeedKph").value(95.0));
        mockMvc.perform(get("/api/v1/vehicles/" + vehicle.id()).header("Authorization", adminToken))
                .andExpect(jsonPath("$.operationalStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.odometerKm").value(15270));
        mockMvc.perform(get("/api/v1/drivers/" + driver.id()).header("Authorization", adminToken))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
        mockMvc.perform(get("/api/v1/vehicles/" + vehicle.id() + "/trips").header("Authorization", adminToken))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].tripNumber").value(startsWith("TRP-")));
        mockMvc.perform(get("/api/v1/drivers/" + driver.id() + "/trips").header("Authorization", adminToken))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(post("/api/v1/trips/" + tripId + "/cancel").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(new TripCancelRequest("too late"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    @DisplayName("overlapping trips for the same vehicle or driver are rejected; only planned trips can be edited")
    void overlapRejected() throws Exception {
        VehicleResponse vehicle = data.compliantVehicle();
        DriverResponse driver = data.data().driver();
        long tripId = create(request(vehicle.id(), driver.id(), tomorrow8, tomorrow8.plus(Duration.ofHours(4))));

        mockMvc.perform(post("/api/v1/trips").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request(vehicle.id(), data.data().driver().id(), tomorrow8.plus(Duration.ofHours(2)), tomorrow8.plus(Duration.ofHours(6))))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VEHICLE_DOUBLE_BOOKED"));
        mockMvc.perform(post("/api/v1/trips").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request(data.compliantVehicle().id(), driver.id(), tomorrow8.plus(Duration.ofHours(2)), tomorrow8.plus(Duration.ofHours(6))))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DRIVER_DOUBLE_BOOKED"));
        // a later window is fine, and the planned trip can be moved
        mockMvc.perform(post("/api/v1/trips").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request(vehicle.id(), driver.id(), tomorrow8.plus(Duration.ofHours(5)), tomorrow8.plus(Duration.ofHours(6))))))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/v1/trips/" + tripId).header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request(vehicle.id(), driver.id(), tomorrow8.plus(Duration.ofHours(5)), tomorrow8.plus(Duration.ofHours(7))))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VEHICLE_DOUBLE_BOOKED"));
        mockMvc.perform(put("/api/v1/trips/" + tripId).header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request(vehicle.id(), driver.id(), tomorrow8.minus(Duration.ofHours(2)), tomorrow8.plus(Duration.ofHours(1))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduledStartAt").exists());
        mockMvc.perform(post("/api/v1/trips/" + tripId + "/cancel").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(new TripCancelRequest("Client cancelled"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("Client cancelled"));
    }

    @Test
    @DisplayName("a VIEWER cannot plan trips; a vehicle without valid documents cannot be planned")
    void viewerForbiddenAndDocumentsRequired() throws Exception {
        VehicleResponse vehicle = data.compliantVehicle();
        DriverResponse driver = data.data().driver();
        mockMvc.perform(post("/api/v1/trips").header("Authorization", tokenFor(Roles.VIEWER))
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(request(vehicle.id(), driver.id(), tomorrow8, null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/v1/trips").header("Authorization", tokenFor(Roles.VIEWER)).param("vehicleId", String.valueOf(vehicle.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(post("/api/v1/trips").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(request(data.data().vehicle().id(), driver.id(), tomorrow8, null))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VEHICLE_DOCUMENTS_INVALID"));
    }

    @Test
    @DisplayName("a driver user only sees the trips of the linked driver profile")
    void myTrips() throws Exception {
        VehicleResponse vehicle = data.compliantVehicle();
        DriverResponse driver = data.data().driver();
        create(request(vehicle.id(), driver.id(), tomorrow8, tomorrow8.plus(Duration.ofHours(1))));
        create(request(data.compliantVehicle().id(), data.data().driver().id(), tomorrow8, tomorrow8.plus(Duration.ofHours(1))));

        String email = "driver" + driver.id() + "@test.limoz.rw";
        userService.create(new CreateUserRequest("Driver", "User", email, null, null, "Test@12345", Set.of(Roles.DRIVER), driver.id(), false));
        String driverToken = "Bearer " + login(email, "Test@12345").accessToken();
        mockMvc.perform(get("/api/v1/trips/mine").header("Authorization", driverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].driver.id").value(driver.id()));
        mockMvc.perform(get("/api/v1/trips/mine").header("Authorization", adminToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("NO_DRIVER_PROFILE"));
    }

    private long create(TripRequest request) throws Exception {
        String body = mockMvc.perform(post("/api/v1/trips").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private static TripRequest request(Long vehicleId, Long driverId, Instant start, Instant end) {
        return new TripRequest(vehicleId, driverId, null, "Kigali", "Musanze", null, "Staff transfer", null, 8, start, end, null);
    }
}
