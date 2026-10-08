package com.limoz.fleet.customer;

import com.limoz.fleet.customer.dto.CommitmentResponse;
import com.limoz.fleet.customer.dto.CustomerResponse;
import com.limoz.fleet.customer.dto.PurchaseOrderReceiveRequest;
import com.limoz.fleet.customer.dto.PurchaseOrderRequest;
import com.limoz.fleet.security.Roles;
import com.limoz.fleet.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PurchaseOrderIT extends AbstractIntegrationTest {

    @Autowired
    CustomerTestData customers;

    private PurchaseOrderRequest request(Long customerId, Long commitmentId, LocalDate issued, LocalDate expiry) {
        return new PurchaseOrderRequest(customerId, commitmentId, null, issued, expiry, null, new BigDecimal("2500000"), "RWF", null, "Q3 shuttle");
    }

    private long create(PurchaseOrderRequest request) throws Exception {
        String body = mockMvc.perform(post("/api/v1/purchase-orders").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    @Test
    @DisplayName("an LPO is numbered LPO-yyyy-nnnn, linked to its commitment and counted on it")
    void createAndReceive() throws Exception {
        CustomerResponse customer = customers.customer();
        CommitmentResponse commitment = customers.activeCommitment(customer.id());
        String body = mockMvc.perform(post("/api/v1/purchase-orders").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request(customer.id(), commitment.id(), LocalDate.now(), LocalDate.now().plusDays(30)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lpoNumber").value(startsWith("LPO-")))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.expired").value(false))
                .andExpect(jsonPath("$.commitment.reference").value(commitment.reference()))
                .andExpect(jsonPath("$.receivedDate").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(body).get("id").asLong();

        mockMvc.perform(post("/api/v1/purchase-orders/" + id + "/receive").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(new PurchaseOrderReceiveRequest(LocalDate.now(), null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receivedDate").value(LocalDate.now().toString()));

        mockMvc.perform(get("/api/v1/commitments/" + commitment.id()).header("Authorization", adminToken))
                .andExpect(jsonPath("$.lpoCount").value(1));
        mockMvc.perform(get("/api/v1/customers/" + customer.id() + "/purchase-orders").header("Authorization", adminToken))
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/v1/purchase-orders").header("Authorization", adminToken)
                        .param("q", commitment.reference()).param("status", "OPEN"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("open LPOs past their expiry date are expired by the status refresh")
    void expiryRefresh() throws Exception {
        CustomerResponse customer = customers.customer();
        long id = create(request(customer.id(), null, LocalDate.now().minusDays(40), LocalDate.now().plusDays(1)));
        mockMvc.perform(put("/api/v1/purchase-orders/" + id).header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request(customer.id(), null, LocalDate.now().minusDays(40), LocalDate.now().minusDays(1)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.expired").value(true));
        mockMvc.perform(post("/api/v1/purchase-orders/refresh-statuses").header("Authorization", adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/purchase-orders/" + id).header("Authorization", adminToken))
                .andExpect(jsonPath("$.status").value("EXPIRED"));

        mockMvc.perform(post("/api/v1/purchase-orders").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request(customer.id(), null, LocalDate.now().minusDays(60), LocalDate.now().minusDays(30)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("EXPIRED"));
    }

    @Test
    @DisplayName("an LPO cannot reference another client's commitment, invalid dates or an unknown booking; cancel is final")
    void validation() throws Exception {
        CustomerResponse customer = customers.customer();
        CustomerResponse other = customers.customer();
        CommitmentResponse foreign = customers.activeCommitment(other.id());
        mockMvc.perform(post("/api/v1/purchase-orders").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request(customer.id(), foreign.id(), LocalDate.now(), null))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("COMMITMENT_CUSTOMER_MISMATCH"));
        mockMvc.perform(post("/api/v1/purchase-orders").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request(customer.id(), null, LocalDate.now(), LocalDate.now().minusDays(1)))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_DATES"));
        mockMvc.perform(post("/api/v1/purchase-orders").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new PurchaseOrderRequest(customer.id(), null, 999999L, LocalDate.now(), null, null, BigDecimal.TEN, null, null, null))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_FOUND"));

        long id = create(request(customer.id(), null, LocalDate.now(), null));
        mockMvc.perform(post("/api/v1/purchase-orders/" + id + "/cancel").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(post("/api/v1/purchase-orders/" + id + "/close").header("Authorization", adminToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        mockMvc.perform(post("/api/v1/purchase-orders").header("Authorization", tokenFor(Roles.VIEWER))
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(request(customer.id(), null, LocalDate.now(), null))))
                .andExpect(status().isForbidden());
    }
}
