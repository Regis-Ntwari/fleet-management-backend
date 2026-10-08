package com.limoz.fleet.fuel;

import com.limoz.fleet.fuel.domain.FuelPaymentMethod;

import com.limoz.fleet.fuel.dto.FuelTransactionRequest;
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
import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FuelIT extends AbstractIntegrationTest {

    @Autowired
    TestData data;

    private FuelTransactionRequest refuel(Long vehicleId, Instant at, long odometer, String litres, BigDecimal sensorLitres, String receipt) {
        return new FuelTransactionRequest(vehicleId, null, null, null, at, "SP Nyabugogo", null, null, new BigDecimal(litres),
                new BigDecimal("1580"), "RWF", odometer, sensorLitres, true, receipt, null, FuelPaymentMethod.FUEL_CARD, null);
    }

    @Test
    @DisplayName("a refuelling computes its cost and, from the previous transaction, distance, consumption and km/L")
    void computedFields() throws Exception {
        VehicleResponse vehicle = data.vehicle();
        Instant now = Instant.now();
        // first transaction: no previous reading -> no distance-based figures
        mockMvc.perform(post("/api/v1/fuel").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(refuel(vehicle.id(), now.minusSeconds(120), 15300, "40", null, "A-1"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAmount").value(63200.00))
                .andExpect(jsonPath("$.previousOdometerKm").doesNotExist())
                .andExpect(jsonPath("$.distanceSinceLastKm").doesNotExist())
                .andExpect(jsonPath("$.consumptionLPer100km").doesNotExist())
                .andExpect(jsonPath("$.anomaly").value(false))
                .andExpect(jsonPath("$.vehicle.plateNumber").value(vehicle.plateNumber()));

        mockMvc.perform(post("/api/v1/fuel").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(refuel(vehicle.id(), now, 15800, "60", null, "A-2"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAmount").value(94800.00))
                .andExpect(jsonPath("$.previousOdometerKm").value(15300))
                .andExpect(jsonPath("$.distanceSinceLastKm").value(500.0))
                .andExpect(jsonPath("$.consumptionLPer100km").value(12.00))
                .andExpect(jsonPath("$.kmPerLitre").value(8.33))
                .andExpect(jsonPath("$.anomaly").value(false))
                .andExpect(jsonPath("$.enteredByUserId").exists());

        // the vehicle odometer follows the latest (non back-dated) reading
        mockMvc.perform(get("/api/v1/vehicles/" + vehicle.id()).header("Authorization", adminToken))
                .andExpect(jsonPath("$.odometerKm").value(15800));
        mockMvc.perform(get("/api/v1/vehicles/" + vehicle.id() + "/odometer").header("Authorization", adminToken))
                .andExpect(jsonPath("$.content[0].source").value("FUEL"));

        // vehicle summary aggregates in the database
        mockMvc.perform(get("/api/v1/vehicles/" + vehicle.id() + "/fuel/summary").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionCount").value(2))
                .andExpect(jsonPath("$.totalLitres").value(100.00))
                .andExpect(jsonPath("$.totalCost").value(158000.00))
                .andExpect(jsonPath("$.totalDistanceKm").value(500.0))
                .andExpect(jsonPath("$.averageConsumptionLPer100km").value(12.00))
                .andExpect(jsonPath("$.averageKmPerLitre").value(8.33))
                .andExpect(jsonPath("$.averagePricePerLitre").value(1580.00))
                .andExpect(jsonPath("$.anomalyCount").value(0));

        LocalDate today = LocalDate.now();
        mockMvc.perform(get("/api/v1/fuel/summary").header("Authorization", adminToken)
                        .param("vehicleId", vehicle.id().toString())
                        .param("from", today.minusDays(1).toString()).param("to", today.plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.transactionCount").value(2))
                .andExpect(jsonPath("$.vehicles.length()").value(1))
                .andExpect(jsonPath("$.vehicles[0].vehicleId").value(vehicle.id()))
                .andExpect(jsonPath("$.vehicles[0].plateNumber").value(vehicle.plateNumber()));

        mockMvc.perform(get("/api/v1/vehicles/" + vehicle.id() + "/fuel").header("Authorization", adminToken).param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].odometerKm").value(15800));
        mockMvc.perform(get("/api/v1/fuel").header("Authorization", adminToken)
                        .param("q", vehicle.plateNumber()).param("from", today.minusDays(1).toString()).param("to", today.plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("high consumption or a sensor variance beyond tolerance flags the transaction as an anomaly")
    void anomalyFlag() throws Exception {
        VehicleResponse vehicle = data.vehicle();
        Instant now = Instant.now();
        mockMvc.perform(post("/api/v1/fuel").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(refuel(vehicle.id(), now.minusSeconds(120), 15100, "40", null, null))))
                .andExpect(status().isCreated());
        // 60 L over 100 km = 60 L/100km (> 25) and sensor saw only 40 L (variance 20 > 5)
        mockMvc.perform(post("/api/v1/fuel").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(refuel(vehicle.id(), now, 15200, "60", new BigDecimal("40"), null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.consumptionLPer100km").value(60.00))
                .andExpect(jsonPath("$.varianceLitres").value(20.00))
                .andExpect(jsonPath("$.anomaly").value(true))
                .andExpect(jsonPath("$.anomalyReason").value(containsString("exceeds threshold")))
                .andExpect(jsonPath("$.anomalyReason").value(containsString("Sensor variance")));

        mockMvc.perform(get("/api/v1/fuel").header("Authorization", adminToken).param("vehicleId", vehicle.id().toString()).param("anomaly", "true"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/v1/vehicles/" + vehicle.id() + "/fuel/summary").header("Authorization", adminToken))
                .andExpect(jsonPath("$.anomalyCount").value(1));
    }

    @Test
    @DisplayName("the same receipt number cannot be recorded twice for a vehicle")
    void duplicateReceipt() throws Exception {
        VehicleResponse vehicle = data.vehicle();
        Instant now = Instant.now();
        mockMvc.perform(post("/api/v1/fuel").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(refuel(vehicle.id(), now.minusSeconds(60), 15100, "40", null, "RCT-100"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/fuel").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(refuel(vehicle.id(), now, 15200, "40", null, "rct-100"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE"));
    }

    @Test
    @DisplayName("odometer cannot go below the vehicle's reading unless the transaction is back-dated; archiving refreshes the next transaction")
    void odometerRule() throws Exception {
        VehicleResponse vehicle = data.vehicle(); // odometer 15000 recorded now
        Instant now = Instant.now();
        mockMvc.perform(post("/api/v1/fuel").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(refuel(vehicle.id(), now.plusSeconds(5), 14000, "40", null, null))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ODOMETER_DECREASE"));

        // back-dated before the vehicle's registration reading: accepted, vehicle odometer untouched
        String backDated = mockMvc.perform(post("/api/v1/fuel").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(refuel(vehicle.id(), now.minusSeconds(3600), 14000, "40", null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.odometerKm").value(14000))
                .andReturn().getResponse().getContentAsString();
        long backDatedId = json.readTree(backDated).get("id").asLong();
        mockMvc.perform(get("/api/v1/vehicles/" + vehicle.id()).header("Authorization", adminToken))
                .andExpect(jsonPath("$.odometerKm").value(15000));

        String later = mockMvc.perform(post("/api/v1/fuel").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(refuel(vehicle.id(), now.plusSeconds(5), 15500, "30", null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.previousOdometerKm").value(14000))
                .andExpect(jsonPath("$.distanceSinceLastKm").value(1500.0))
                .andReturn().getResponse().getContentAsString();
        long laterId = json.readTree(later).get("id").asLong();

        // archiving the earlier transaction removes the distance basis of the later one
        mockMvc.perform(delete("/api/v1/fuel/" + backDatedId).header("Authorization", adminToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/fuel/" + laterId).header("Authorization", adminToken))
                .andExpect(jsonPath("$.previousOdometerKm").doesNotExist())
                .andExpect(jsonPath("$.distanceSinceLastKm").doesNotExist());
        mockMvc.perform(get("/api/v1/fuel").header("Authorization", adminToken).param("vehicleId", vehicle.id().toString()))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/v1/fuel").header("Authorization", adminToken).param("vehicleId", vehicle.id().toString()).param("archived", "true"))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("a VIEWER can read the fuel log but not record refuellings; litres must be positive")
    void authorizationAndValidation() throws Exception {
        VehicleResponse vehicle = data.vehicle();
        String viewer = tokenFor(Roles.VIEWER);
        mockMvc.perform(get("/api/v1/fuel").header("Authorization", viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(0)));
        mockMvc.perform(post("/api/v1/fuel").header("Authorization", viewer)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(refuel(vehicle.id(), null, 15100, "40", null, null))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/fuel").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(refuel(vehicle.id(), null, 15100, "0", null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
