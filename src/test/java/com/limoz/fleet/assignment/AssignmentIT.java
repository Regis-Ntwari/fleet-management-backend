package com.limoz.fleet.assignment;

import com.limoz.fleet.assignment.dto.AssignmentRequest;
import com.limoz.fleet.assignment.dto.EndAssignmentRequest;
import com.limoz.fleet.driver.dto.DriverResponse;
import com.limoz.fleet.support.AbstractIntegrationTest;
import com.limoz.fleet.support.TestData;
import com.limoz.fleet.vehicle.dto.VehicleResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AssignmentIT extends AbstractIntegrationTest {

    @Autowired
    TestData data;

    @Test
    @DisplayName("assigning a vehicle to a driver updates both statuses and ending it restores them, keeping history")
    void assignAndEnd() throws Exception {
        VehicleResponse vehicle = data.vehicle();
        DriverResponse driver = data.driver();
        String created = mockMvc.perform(post("/api/v1/assignments").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new AssignmentRequest(vehicle.id(), driver.id(), null, "Daily operations", 15200L, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.vehicle.plateNumber").value(vehicle.plateNumber()))
                .andReturn().getResponse().getContentAsString();
        long assignmentId = json.readTree(created).get("id").asLong();

        mockMvc.perform(get("/api/v1/vehicles/" + vehicle.id()).header("Authorization", adminToken))
                .andExpect(jsonPath("$.operationalStatus").value("ASSIGNED"))
                .andExpect(jsonPath("$.currentDriverId").value(driver.id()))
                .andExpect(jsonPath("$.odometerKm").value(15200));
        mockMvc.perform(get("/api/v1/drivers/" + driver.id()).header("Authorization", adminToken))
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.currentVehicleId").value(vehicle.id()));

        mockMvc.perform(post("/api/v1/assignments/" + assignmentId + "/end").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new EndAssignmentRequest(null, 15650L, "Returned clean", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.distanceKm").value(450));

        mockMvc.perform(get("/api/v1/vehicles/" + vehicle.id()).header("Authorization", adminToken))
                .andExpect(jsonPath("$.operationalStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.currentDriverId").doesNotExist());
        mockMvc.perform(get("/api/v1/assignments/vehicle/" + vehicle.id()).header("Authorization", adminToken))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("a vehicle or driver that is already assigned cannot be assigned again")
    void conflictingAssignmentsRejected() throws Exception {
        VehicleResponse v1 = data.vehicle();
        VehicleResponse v2 = data.vehicle();
        DriverResponse d1 = data.driver();
        DriverResponse d2 = data.driver();
        mockMvc.perform(post("/api/v1/assignments").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(new AssignmentRequest(v1.id(), d1.id(), null, null, null, null))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/assignments").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(new AssignmentRequest(v1.id(), d2.id(), null, null, null, null))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VEHICLE_ALREADY_ASSIGNED"));
        mockMvc.perform(post("/api/v1/assignments").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(new AssignmentRequest(v2.id(), d1.id(), null, null, null, null))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DRIVER_ALREADY_ASSIGNED"));
    }

    @Test
    @DisplayName("a driver with an expired licence cannot be assigned")
    void expiredLicenseRejected() throws Exception {
        VehicleResponse vehicle = data.vehicle();
        DriverResponse driver = data.driverWithExpiredLicense();
        mockMvc.perform(post("/api/v1/assignments").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(new AssignmentRequest(vehicle.id(), driver.id(), null, null, null, null))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DRIVER_LICENSE_EXPIRED"));
    }
}
