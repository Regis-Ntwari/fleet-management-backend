package com.limoz.fleet.customer;

import com.limoz.fleet.customer.domain.Commitment;

import com.limoz.fleet.customer.dto.CommitmentRequest;
import com.limoz.fleet.customer.dto.CustomerResponse;
import com.limoz.fleet.security.Roles;
import com.limoz.fleet.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CommitmentIT extends AbstractIntegrationTest {

    @Autowired
    CustomerTestData customers;

    private long create(CommitmentRequest request) throws Exception {
        String body = mockMvc.perform(post("/api/v1/commitments").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    @Test
    @DisplayName("a commitment is numbered CMT-nnnn, starts as DRAFT and reports zero consumption without bookings")
    void createCommitment() throws Exception {
        CustomerResponse customer = customers.customer();
        mockMvc.perform(post("/api/v1/commitments").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(customers.commitmentRequest(customer.id()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference").value(startsWith("CMT-")))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.customer.id").value(customer.id()))
                .andExpect(jsonPath("$.contractedValue").value(12000000))
                .andExpect(jsonPath("$.consumedValue").value(0))
                .andExpect(jsonPath("$.remainingValue").value(12000000))
                .andExpect(jsonPath("$.utilisationPercent").value(0.0))
                .andExpect(jsonPath("$.lpoCount").value(0));

        mockMvc.perform(get("/api/v1/customers/" + customer.id() + "/commitments").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/v1/customers/" + customer.id() + "/summary").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commitments").value(1))
                .andExpect(jsonPath("$.activeBookings").value(0))
                .andExpect(jsonPath("$.totalBilled").value(0));
    }

    @Test
    @DisplayName("activate and close follow the lifecycle; closed commitments cannot be reactivated or edited")
    void lifecycle() throws Exception {
        CustomerResponse customer = customers.customer();
        long id = create(customers.commitmentRequest(customer.id()));
        mockMvc.perform(post("/api/v1/commitments/" + id + "/close").header("Authorization", adminToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        mockMvc.perform(post("/api/v1/commitments/" + id + "/activate").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mockMvc.perform(get("/api/v1/commitments/summaries").header("Authorization", adminToken).param("customerId", String.valueOf(customer.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(post("/api/v1/commitments/" + id + "/close").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
        mockMvc.perform(post("/api/v1/commitments/" + id + "/activate").header("Authorization", adminToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        mockMvc.perform(post("/api/v1/commitments/" + id + "/cancel").header("Authorization", adminToken))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(get("/api/v1/audit-logs/entity/Commitment/" + id).header("Authorization", adminToken))
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(3)));
    }

    @Test
    @DisplayName("a commitment ending within the warning window becomes EXPIRING_SOON, and one that has ended closes on refresh")
    void statusRefresh() throws Exception {
        CustomerResponse customer = customers.customer();
        CommitmentRequest base = customers.commitmentRequest(customer.id());
        long soon = create(new CommitmentRequest(customer.id(), base.title(), LocalDate.now().minusMonths(11), LocalDate.now().plusDays(5),
                base.contractedValue(), "USD", null, null));
        mockMvc.perform(post("/api/v1/commitments/" + soon + "/activate").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXPIRING_SOON"))
                .andExpect(jsonPath("$.currency").value("USD"));

        long ended = create(new CommitmentRequest(customer.id(), base.title() + " old", LocalDate.now().minusYears(2), LocalDate.now().minusYears(1),
                base.contractedValue(), null, null, null));
        mockMvc.perform(post("/api/v1/commitments/" + ended + "/activate").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
        mockMvc.perform(post("/api/v1/commitments/refresh-statuses").header("Authorization", adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/commitments").header("Authorization", adminToken)
                        .param("customerId", String.valueOf(customer.id())).param("status", "EXPIRING_SOON"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("period, currency and authorization rules")
    void validation() throws Exception {
        CustomerResponse customer = customers.customer();
        CommitmentRequest base = customers.commitmentRequest(customer.id());
        mockMvc.perform(post("/api/v1/commitments").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new CommitmentRequest(customer.id(), base.title(), base.periodEnd(), base.periodStart(), base.contractedValue(), "RWF", null, null))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_PERIOD"));
        mockMvc.perform(post("/api/v1/commitments").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new CommitmentRequest(customer.id(), base.title(), base.periodStart(), base.periodEnd(), base.contractedValue(), "EUR", null, null))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_CURRENCY"));
        mockMvc.perform(post("/api/v1/commitments").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new CommitmentRequest(customer.id(), base.title(), base.periodStart(), base.periodEnd(), new BigDecimal("-1"), null, null, null))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/commitments").header("Authorization", tokenFor(Roles.VIEWER))
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(base)))
                .andExpect(status().isForbidden());
    }
}
