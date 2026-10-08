package com.limoz.fleet.finance;

import com.limoz.fleet.finance.domain.PaymentMethod;
import com.limoz.fleet.finance.repository.ExpenseCategoryRepository;

import com.limoz.fleet.finance.dto.ExpenseRejectRequest;
import com.limoz.fleet.finance.dto.ExpenseRequest;
import com.limoz.fleet.finance.dto.SettlementRequest;
import com.limoz.fleet.security.Roles;
import com.limoz.fleet.support.AbstractIntegrationTest;
import com.limoz.fleet.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExpenseIT extends AbstractIntegrationTest {

    @Autowired
    TestData data;

    @Autowired
    ExpenseCategoryRepository categoryRepository;

    private Long categoryId(String code) {
        return categoryRepository.findAll().stream().filter(c -> c.getCode().equals(code)).findFirst().orElseThrow().getId();
    }

    private ExpenseRequest request(Long vehicleId, LocalDate incurredOn) {
        return new ExpenseRequest(categoryId("PERMITS"), "RURA route permit renewal", new BigDecimal("42000"), "RWF", incurredOn,
                vehicleId, null, null, null, null, null);
    }

    private long submit(String token, Long vehicleId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/expenses").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(request(vehicleId, LocalDate.now()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    @Test
    @DisplayName("seeded expense categories are available as reference data")
    void categories() throws Exception {
        mockMvc.perform(get("/api/v1/expense-categories").header("Authorization", tokenFor(Roles.VIEWER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code").value(hasItem("TOLLS_PARKING")));
    }

    @Test
    @DisplayName("submit -> approve -> pay: approval needs EXPENSE_APPROVE and paying writes an OUT payment")
    void approveAndPay() throws Exception {
        Long vehicleId = data.vehicle().id();
        String finance = tokenFor(Roles.FINANCE);
        String viewer = tokenFor(Roles.VIEWER);
        String body = mockMvc.perform(post("/api/v1/expenses").header("Authorization", finance)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(request(vehicleId, LocalDate.now()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expenseNumber").value(startsWith("EXP-")))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.category.code").value("PERMITS"))
                .andExpect(jsonPath("$.submittedByName").exists())
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(body).get("id").asLong();

        mockMvc.perform(post("/api/v1/expenses/" + id + "/pay").header("Authorization", finance)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(new SettlementRequest(PaymentMethod.CASH, null, null, null, null, null))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));

        mockMvc.perform(post("/api/v1/expenses/" + id + "/approve").header("Authorization", viewer))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/expenses/" + id + "/approve").header("Authorization", finance))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approvedAt").exists())
                .andExpect(jsonPath("$.approvedByUserId").exists());

        mockMvc.perform(put("/api/v1/expenses/" + id).header("Authorization", finance)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(request(vehicleId, LocalDate.now()))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXPENSE_NOT_PENDING"));

        mockMvc.perform(post("/api/v1/expenses/" + id + "/pay").header("Authorization", finance)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new SettlementRequest(PaymentMethod.CASH, null, "Claudine M.", null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
        mockMvc.perform(get("/api/v1/payments").header("Authorization", finance).param("expenseId", String.valueOf(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].direction").value("OUT"))
                .andExpect(jsonPath("$.content[0].amount").value(42000))
                .andExpect(jsonPath("$.content[0].counterpartyName").value("Claudine M."));

        mockMvc.perform(get("/api/v1/vehicles/" + vehicleId + "/costs").header("Authorization", finance))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expenses").value(42000))
                .andExpect(jsonPath("$.totalCost").value(42000));
        mockMvc.perform(get("/api/v1/expenses/summary").header("Authorization", finance))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.countByStatus.PAID").value(greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("rejected expenses record the reason and leave the workflow")
    void reject() throws Exception {
        long id = submit(adminToken, null);
        mockMvc.perform(post("/api/v1/expenses/" + id + "/reject").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(new ExpenseRejectRequest("No receipt attached"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("No receipt attached"));
        mockMvc.perform(post("/api/v1/expenses/" + id + "/approve").header("Authorization", adminToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        mockMvc.perform(get("/api/v1/expenses").header("Authorization", adminToken).param("status", "REJECTED").param("q", "permit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("an expense cannot be dated in the future and only the submitter or finance may edit it")
    void validationAndOwnership() throws Exception {
        mockMvc.perform(post("/api/v1/expenses").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(request(null, LocalDate.now().plusDays(1)))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXPENSE_DATE_IN_FUTURE"));

        long id = submit(adminToken, null);
        mockMvc.perform(put("/api/v1/expenses/" + id).header("Authorization", tokenFor(Roles.VIEWER))
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(request(null, LocalDate.now()))))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/expenses/" + id).header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(request(null, LocalDate.now().minusDays(1)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incurredOn").value(LocalDate.now().minusDays(1).toString()));
    }
}
