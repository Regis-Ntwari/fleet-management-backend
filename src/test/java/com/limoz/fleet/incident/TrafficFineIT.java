package com.limoz.fleet.incident;

import com.limoz.fleet.finance.PaymentMethod;
import com.limoz.fleet.finance.dto.SettlementRequest;
import com.limoz.fleet.incident.dto.FineNoteRequest;
import com.limoz.fleet.incident.dto.TrafficFineRequest;
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
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TrafficFineIT extends AbstractIntegrationTest {

    @Autowired
    TestData data;

    private TrafficFineRequest request(Long vehicleId) {
        return new TrafficFineRequest(vehicleId, null, null, "RNP-77812", Instant.now().minus(1, ChronoUnit.DAYS), "Kigali-Rubavu road",
                "Over-speeding", new BigDecimal("25000"), "RWF", LocalDate.now().plusDays(14), false, null, null);
    }

    private long record(Long vehicleId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/traffic-fines").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(request(vehicleId))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    @Test
    @DisplayName("a fine is numbered, starts UNPAID and appears on the vehicle's fine list")
    void recordFine() throws Exception {
        VehicleResponse vehicle = data.vehicle();
        mockMvc.perform(post("/api/v1/traffic-fines").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(request(vehicle.id()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fineNumber").value(startsWith("FN-")))
                .andExpect(jsonPath("$.status").value("UNPAID"))
                .andExpect(jsonPath("$.amount").value(25000))
                .andExpect(jsonPath("$.vehicle.plateNumber").value(vehicle.plateNumber()));
        mockMvc.perform(get("/api/v1/vehicles/" + vehicle.id() + "/fines").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/v1/traffic-fines/summary").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unpaidCount").value(greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("paying a fine records an OUT payment in the ledger and links it to the fine")
    void payCreatesLedgerEntry() throws Exception {
        long id = record(data.vehicle().id());
        String paid = mockMvc.perform(post("/api/v1/traffic-fines/" + id + "/pay").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new SettlementRequest(PaymentMethod.MOBILE_MONEY, null, null, "MOMO-123", null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paidAt").exists())
                .andExpect(jsonPath("$.paymentId").exists())
                .andReturn().getResponse().getContentAsString();
        long paymentId = json.readTree(paid).get("paymentId").asLong();

        mockMvc.perform(get("/api/v1/payments/" + paymentId).header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.direction").value("OUT"))
                .andExpect(jsonPath("$.trafficFineId").value(id))
                .andExpect(jsonPath("$.amount").value(25000))
                .andExpect(jsonPath("$.method").value("MOBILE_MONEY"))
                .andExpect(jsonPath("$.paymentNumber").value(startsWith("PAY-")));

        mockMvc.perform(post("/api/v1/traffic-fines/" + id + "/pay").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new SettlementRequest(PaymentMethod.CASH, null, null, null, null, null))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    @DisplayName("dispute and waive are explicit transitions; a waived fine is final")
    void disputeAndWaive() throws Exception {
        long id = record(data.vehicle().id());
        mockMvc.perform(post("/api/v1/traffic-fines/" + id + "/dispute").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(new FineNoteRequest("Camera timestamp wrong"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPUTED"));
        mockMvc.perform(post("/api/v1/traffic-fines/" + id + "/dispute").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(new FineNoteRequest("again"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        mockMvc.perform(post("/api/v1/traffic-fines/" + id + "/waive").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(new FineNoteRequest("Dispute upheld"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAIVED"));
        mockMvc.perform(post("/api/v1/traffic-fines/" + id + "/pay").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new SettlementRequest(PaymentMethod.CASH, null, null, null, null, null))))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(get("/api/v1/traffic-fines").header("Authorization", adminToken).param("status", "WAIVED").param("q", "speeding"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("viewers can read fines but only FINE_MANAGE holders can record them")
    void authorization() throws Exception {
        VehicleResponse vehicle = data.vehicle();
        String viewer = tokenFor(Roles.VIEWER);
        mockMvc.perform(get("/api/v1/traffic-fines").header("Authorization", viewer)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/traffic-fines").header("Authorization", viewer)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(request(vehicle.id()))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/traffic-fines").header("Authorization", tokenFor(Roles.COMPLIANCE_OFFICER))
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(request(vehicle.id()))))
                .andExpect(status().isCreated());
    }
}
