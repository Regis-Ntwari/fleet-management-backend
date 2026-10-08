package com.limoz.fleet.vehicle;

import com.limoz.fleet.security.Roles;
import com.limoz.fleet.support.AbstractIntegrationTest;
import com.limoz.fleet.support.TestData;
import com.limoz.fleet.vehicle.dto.OdometerCorrectionRequest;
import com.limoz.fleet.vehicle.dto.VehicleRequest;
import com.limoz.fleet.vehicle.dto.VehicleResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VehicleIT extends AbstractIntegrationTest {

    @Autowired
    TestData data;

    @Test
    @DisplayName("vehicle can be created and plate number is normalised")
    void createVehicle() throws Exception {
        VehicleRequest request = data.vehicleRequest("  rad  901  z ");
        mockMvc.perform(post("/api/v1/vehicles").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plateNumber").value("RAD 901 Z"))
                .andExpect(jsonPath("$.operationalStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.categoryName").value("Minibus"))
                .andExpect(jsonPath("$.odometerKm").value(15000));
    }

    @Test
    @DisplayName("duplicate plate numbers are rejected with 409 even with different spacing/case")
    void duplicatePlateRejected() throws Exception {
        mockMvc.perform(post("/api/v1/vehicles").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(data.vehicleRequest("RAD 777 D"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/vehicles").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(data.vehicleRequest("rad777d"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE"))
                .andExpect(jsonPath("$.message").value("Vehicle plate number RAD777D already exists"));
    }

    @Test
    @DisplayName("bean validation errors are reported per field")
    void validationErrors() throws Exception {
        String body = "{\"plateNumber\":\"\",\"make\":\"Toyota\",\"model\":\"\",\"fuelType\":\"DIESEL\"}";
        mockMvc.perform(post("/api/v1/vehicles").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.length()").value(greaterThanOrEqualTo(3)));
    }

    @Test
    @DisplayName("a VIEWER can read but not create vehicles (server-side authorization)")
    void viewerIsReadOnly() throws Exception {
        String viewer = tokenFor(Roles.VIEWER);
        mockMvc.perform(get("/api/v1/vehicles").header("Authorization", viewer))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/vehicles").header("Authorization", viewer)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(data.vehicleRequest("RAD 555 V"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("search supports server-side paging, filtering and sorting")
    void searchIsPaginated() throws Exception {
        data.vehicle();
        data.vehicle();
        mockMvc.perform(get("/api/v1/vehicles").header("Authorization", adminToken)
                        .param("page", "0").param("size", "1").param("sort", "plateNumber,desc").param("status", "AVAILABLE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.first").value(true));
    }

    @Test
    @DisplayName("odometer cannot decrease without an approved correction, and corrections are journaled")
    void odometerRules() throws Exception {
        VehicleResponse vehicle = data.vehicle();
        VehicleRequest lower = data.vehicleRequest(vehicle.plateNumber());
        VehicleRequest lowered = new VehicleRequest(lower.plateNumber(), lower.fleetNumber(), lower.make(), lower.model(), lower.modelYear(),
                lower.categoryId(), lower.bodyType(), lower.fuelType(), lower.transmission(), lower.engineNumber(), lower.chassisNumber(),
                lower.color(), 1000L, lower.seatingCapacity(), lower.purchaseDate(), lower.acquisitionCost(), lower.ownershipType(),
                null, null, null, lower.insuranceProvider(), lower.insurancePolicyNumber(), lower.insuranceExpiryDate(), lower.dayRate(),
                lower.department(), null);
        mockMvc.perform(put("/api/v1/vehicles/" + vehicle.id()).header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(lowered)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ODOMETER_DECREASE"));

        mockMvc.perform(post("/api/v1/vehicles/" + vehicle.id() + "/odometer/correct").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(new OdometerCorrectionRequest(1000L, "Dashboard replaced"))))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/vehicles/" + vehicle.id() + "/odometer").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].source").value("CORRECTION"))
                .andExpect(jsonPath("$.content[0].readingKm").value(1000))
                .andExpect(jsonPath("$.content[0].correctionReason").value("Dashboard replaced"));
        mockMvc.perform(get("/api/v1/audit-logs/entity/Vehicle/" + vehicle.id()).header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("CORRECTION"));
    }

    @Test
    @DisplayName("archiving is a soft delete and the vehicle disappears from default searches")
    void archiveIsSoft() throws Exception {
        VehicleResponse vehicle = data.vehicle();
        mockMvc.perform(delete("/api/v1/vehicles/" + vehicle.id()).header("Authorization", adminToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/vehicles/" + vehicle.id()).header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true))
                .andExpect(jsonPath("$.operationalStatus").value("INACTIVE"));
        mockMvc.perform(get("/api/v1/vehicles").header("Authorization", adminToken).param("q", vehicle.plateNumber()))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/v1/vehicles").header("Authorization", adminToken).param("q", vehicle.plateNumber()).param("archived", "true"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}
