package com.limoz.fleet.maintenance;

import com.limoz.fleet.maintenance.inventory.StockMovementType;
import com.limoz.fleet.maintenance.inventory.dto.SparePartRequest;
import com.limoz.fleet.maintenance.inventory.dto.StockMovementRequest;
import com.limoz.fleet.security.Roles;
import com.limoz.fleet.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InventoryIT extends AbstractIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger(1000);

    private SparePartRequest part(String number, int minimum, int opening) {
        return new SparePartRequest(number, "Brake pads (front)", "Brakes", "pcs", new BigDecimal("45000"), "CFAO Motors", minimum, opening,
                "Shelf A1", true, null);
    }

    private long createPart(String number, int minimum, int opening) throws Exception {
        String body = mockMvc.perform(post("/api/v1/spare-parts").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(part(number, minimum, opening))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    @Test
    @DisplayName("parts are catalogued with a normalised unique part number and a derived stock status")
    void partCrud() throws Exception {
        String number = "bp-" + SEQ.incrementAndGet();
        String body = mockMvc.perform(post("/api/v1/spare-parts").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(part(" " + number + " ", 8, 6))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.partNumber").value(number.toUpperCase()))
                .andExpect(jsonPath("$.currentStock").value(6))
                .andExpect(jsonPath("$.stockStatus").value("LOW"))
                .andExpect(jsonPath("$.stockValue").value(270000.00))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(body).get("id").asLong();

        mockMvc.perform(post("/api/v1/spare-parts").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(part(number.toUpperCase(), 8, 0))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE"));

        // opening stock is booked as an IN movement
        mockMvc.perform(get("/api/v1/spare-parts/" + id + "/movements").header("Authorization", adminToken))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].movementType").value("IN"))
                .andExpect(jsonPath("$.content[0].quantity").value(6))
                .andExpect(jsonPath("$.content[0].balanceAfter").value(6))
                .andExpect(jsonPath("$.content[0].referenceType").value("OPENING_STOCK"));

        SparePartRequest renamed = new SparePartRequest(number, "Brake pads (front, ceramic)", "Brakes", "pcs", new BigDecimal("48000"),
                "CFAO Motors", 2, 99, null, true, "ceramic");
        mockMvc.perform(put("/api/v1/spare-parts/" + id).header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(renamed)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Brake pads (front, ceramic)"))
                .andExpect(jsonPath("$.currentStock").value(6))
                .andExpect(jsonPath("$.minimumStock").value(2))
                .andExpect(jsonPath("$.stockStatus").value("OK"));

        mockMvc.perform(get("/api/v1/spare-parts").header("Authorization", adminToken).param("q", number).param("status", "OK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id));
        mockMvc.perform(get("/api/v1/spare-parts/summary").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partCount").value(greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("stock movements update the balance atomically, generate PO numbers for receipts and refuse to go below zero")
    void movements() throws Exception {
        long id = createPart("of-" + SEQ.incrementAndGet(), 5, 0);
        mockMvc.perform(get("/api/v1/spare-parts/" + id).header("Authorization", adminToken))
                .andExpect(jsonPath("$.stockStatus").value("OUT"));

        mockMvc.perform(post("/api/v1/stock-movements").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new StockMovementRequest(id, StockMovementType.IN, 10, new BigDecimal("44000"), null, null, null, "Delivery"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(10))
                .andExpect(jsonPath("$.balanceAfter").value(10))
                .andExpect(jsonPath("$.referenceType").value("PURCHASE_ORDER"))
                .andExpect(jsonPath("$.referenceNumber").value(startsWith("PO-")))
                .andExpect(jsonPath("$.performedByName").exists());

        mockMvc.perform(post("/api/v1/stock-movements").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new StockMovementRequest(id, StockMovementType.OUT, 11, null, null, "MNT-2026-0001", null, null))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

        mockMvc.perform(post("/api/v1/stock-movements").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new StockMovementRequest(id, StockMovementType.OUT, 7, null, null, "MNT-2026-0001", null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(-7))
                .andExpect(jsonPath("$.balanceAfter").value(3))
                .andExpect(jsonPath("$.referenceNumber").value("MNT-2026-0001"));

        mockMvc.perform(post("/api/v1/stock-movements").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new StockMovementRequest(id, StockMovementType.ADJUSTMENT, -3, null, "STOCK_COUNT", null, null, "Count"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.balanceAfter").value(0));

        mockMvc.perform(post("/api/v1/stock-movements").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new StockMovementRequest(id, StockMovementType.IN, 0, null, null, null, null, null))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_QUANTITY"));

        mockMvc.perform(get("/api/v1/spare-parts/" + id).header("Authorization", adminToken))
                .andExpect(jsonPath("$.currentStock").value(0))
                .andExpect(jsonPath("$.stockStatus").value("OUT"));
        mockMvc.perform(get("/api/v1/spare-parts/low-stock").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")].stockStatus").value("OUT"));
        mockMvc.perform(get("/api/v1/stock-movements").header("Authorization", adminToken)
                        .param("sparePartId", String.valueOf(id)).param("movementType", "OUT"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/v1/stock-movements").header("Authorization", adminToken).param("reference", "MNT-2026-0001"))
                .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("a VIEWER can read the store but cannot change it")
    void viewerIsReadOnly() throws Exception {
        long id = createPart("af-" + SEQ.incrementAndGet(), 2, 4);
        String viewer = tokenFor(Roles.VIEWER);
        mockMvc.perform(get("/api/v1/spare-parts").header("Authorization", viewer)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/stock-movements").header("Authorization", viewer)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/spare-parts").header("Authorization", viewer)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(part("xx-" + SEQ.incrementAndGet(), 1, 1))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(post("/api/v1/stock-movements").header("Authorization", viewer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new StockMovementRequest(id, StockMovementType.IN, 1, null, null, null, null, null))))
                .andExpect(status().isForbidden());
    }
}
