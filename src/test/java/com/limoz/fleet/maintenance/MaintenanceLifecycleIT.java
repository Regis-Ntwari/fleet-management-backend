package com.limoz.fleet.maintenance;

import com.limoz.fleet.maintenance.dto.MaintenanceCancelRequest;
import com.limoz.fleet.maintenance.dto.MaintenanceCommentRequest;
import com.limoz.fleet.maintenance.dto.MaintenanceCompleteRequest;
import com.limoz.fleet.maintenance.dto.MaintenancePartRequest;
import com.limoz.fleet.maintenance.dto.MaintenancePaymentRequest;
import com.limoz.fleet.maintenance.dto.MaintenanceRequest;
import com.limoz.fleet.maintenance.dto.MaintenanceReviewRequest;
import com.limoz.fleet.maintenance.dto.MaintenanceTaskRequest;
import com.limoz.fleet.maintenance.dto.MaintenanceTaskStatusRequest;
import com.limoz.fleet.maintenance.dto.PartRejectRequest;
import com.limoz.fleet.maintenance.inventory.dto.SparePartRequest;
import com.limoz.fleet.security.Roles;
import com.limoz.fleet.support.AbstractIntegrationTest;
import com.limoz.fleet.support.TestData;
import com.limoz.fleet.vehicle.dto.VehicleResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MaintenanceLifecycleIT extends AbstractIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger(5000);

    @Autowired
    TestData data;

    private long workshopId() throws Exception {
        String body = mockMvc.perform(get("/api/v1/workshops").header("Authorization", adminToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get(0).get("id").asLong();
    }

    private long serviceTypeId(String code) throws Exception {
        String body = mockMvc.perform(get("/api/v1/service-types").header("Authorization", adminToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (JsonNode node : json.readTree(body)) {
            if (code.equals(node.get("code").asText())) return node.get("id").asLong();
        }
        throw new IllegalStateException("service type " + code + " not seeded");
    }

    private long sparePart(int stock, String unitCost) throws Exception {
        SparePartRequest request = new SparePartRequest("OF-" + SEQ.incrementAndGet(), "Oil filter", "Filters", "pcs", new BigDecimal(unitCost),
                null, 2, stock, null, true, null);
        String body = mockMvc.perform(post("/api/v1/spare-parts").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(request)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private MaintenanceRequest job(Long vehicleId, Long workshopId, Long odometer, String laborCost) {
        return new MaintenanceRequest(vehicleId, null, null, "420002 GARDEN FRESH Ltd", "Distribution", null, "HABIMANA Joseph", "0788123456",
                "Engine noise and warning light", "Scratch on rear bumper, fuel 1/2, spare wheel present", MaintenanceType.CORRECTIVE,
                Priority.HIGH, workshopId, null, "Eric the mechanic", null, null, odometer, null,
                laborCost == null ? null : new BigDecimal(laborCost), null, null);
    }

    private JsonNode postJson(String path, Object body, int expected) throws Exception {
        var request = post(path).header("Authorization", adminToken).contentType(MediaType.APPLICATION_JSON);
        if (body != null) request = request.content(toJson(body));
        String response = mockMvc.perform(request).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return response.isEmpty() ? null : json.readTree(response);
    }

    @Test
    @DisplayName("garage flow: intake -> review with parts -> approve -> start -> parts from stock -> complete -> payment -> gate pass")
    void garageIntakeToGatePass() throws Exception {
        VehicleResponse vehicle = data.vehicle(); // odometer 15000
        long workshop = workshopId();
        long engineOil = serviceTypeId("ENGINE_OIL");
        long filter = sparePart(10, "12000");

        JsonNode created = postJson("/api/v1/maintenance/intake", job(vehicle.id(), workshop, 15400L, "10000"), 201);
        long id = created.get("job").get("id").asLong();
        String mnt = created.get("job").get("maintenanceNumber").asText();
        mockMvc.perform(get("/api/v1/maintenance/" + id).header("Authorization", adminToken))
                .andExpect(jsonPath("$.job.maintenanceNumber").value(startsWith("MNT-")))
                .andExpect(jsonPath("$.job.intakeNumber").value(matchesPattern("GRG/\\d{6}/\\d{4}")))
                .andExpect(jsonPath("$.job.status").value("REPORTED"))
                .andExpect(jsonPath("$.job.ownerName").value("420002 GARDEN FRESH Ltd"))
                .andExpect(jsonPath("$.job.workshopName").value("LIMOZ Internal Garage"))
                .andExpect(jsonPath("$.job.daysInGarage").value(0))
                .andExpect(jsonPath("$.job.daysInGarageBand").value("NORMAL"))
                .andExpect(jsonPath("$.comments[0].commentType").value("SYSTEM"));
        mockMvc.perform(get("/api/v1/vehicles/" + vehicle.id()).header("Authorization", adminToken))
                .andExpect(jsonPath("$.odometerKm").value(15400))
                .andExpect(jsonPath("$.operationalStatus").value("AVAILABLE"));

        // mechanic review requests two parts: one from stock, one sourced externally
        MaintenanceReviewRequest review = new MaintenanceReviewRequest("Worn oil filter causing pressure drop",
                List.of("Oil pressure warning", "Ticking noise at idle"), "Replace oil filter and gasket", "2 hours, bay 1", null, null,
                List.of(new MaintenancePartRequest(filter, null, null, 2, null),
                        new MaintenancePartRequest(null, "Sump gasket", "GSK-1", 1, new BigDecimal("5000"))));
        JsonNode reviewed = postJson("/api/v1/maintenance/" + id + "/review", review, 200);
        mockMvc.perform(get("/api/v1/maintenance/" + id).header("Authorization", adminToken))
                .andExpect(jsonPath("$.job.status").value("INSPECTION"))
                .andExpect(jsonPath("$.job.observedFaults").value("Oil pressure warning\nTicking noise at idle"))
                .andExpect(jsonPath("$.parts.length()").value(2))
                .andExpect(jsonPath("$.parts[0].status").value("REQUESTED"))
                .andExpect(jsonPath("$.parts[0].partName").value("Oil filter"))
                .andExpect(jsonPath("$.parts[0].lineTotal").value(24000.00))
                .andExpect(jsonPath("$.partsAwaitingApproval").value(2))
                .andExpect(jsonPath("$.job.partsCost").value(0));
        long stockPartId = reviewed.get("parts").get(0).get("id").asLong();
        long externalPartId = reviewed.get("parts").get(1).get("id").asLong();

        postJson("/api/v1/maintenance/" + id + "/approve", null, 200);
        mockMvc.perform(get("/api/v1/maintenance/" + id).header("Authorization", adminToken))
                .andExpect(jsonPath("$.job.status").value("APPROVED"))
                .andExpect(jsonPath("$.job.approvedByUserId").exists());

        JsonNode withTask = postJson("/api/v1/maintenance/" + id + "/tasks",
                new MaintenanceTaskRequest(engineOil, "Engine oil and filter change", new BigDecimal("2"), new BigDecimal("20000"), null, null), 201);
        long taskId = withTask.get("tasks").get(0).get("id").asLong();

        // start: vehicle enters the workshop
        postJson("/api/v1/maintenance/" + id + "/start", null, 200);
        mockMvc.perform(get("/api/v1/vehicles/" + vehicle.id()).header("Authorization", adminToken))
                .andExpect(jsonPath("$.operationalStatus").value("IN_MAINTENANCE"))
                .andExpect(jsonPath("$.maintenanceStatus").value("IN_WORKSHOP"));

        // approving the stock part issues it; the external part is simply approved
        postJson("/api/v1/maintenance/" + id + "/parts/" + stockPartId + "/approve", null, 200);
        postJson("/api/v1/maintenance/" + id + "/parts/" + externalPartId + "/approve", null, 200);
        mockMvc.perform(get("/api/v1/maintenance/" + id).header("Authorization", adminToken))
                .andExpect(jsonPath("$.parts[0].status").value("ISSUED"))
                .andExpect(jsonPath("$.parts[0].stockMovementId").exists())
                .andExpect(jsonPath("$.parts[1].status").value("APPROVED"))
                .andExpect(jsonPath("$.job.partsCost").value(29000.00))
                .andExpect(jsonPath("$.job.totalCost").value(39000.00))
                .andExpect(jsonPath("$.partsAwaitingApproval").value(0));
        mockMvc.perform(get("/api/v1/spare-parts/" + filter).header("Authorization", adminToken))
                .andExpect(jsonPath("$.currentStock").value(8));
        mockMvc.perform(get("/api/v1/stock-movements").header("Authorization", adminToken).param("maintenanceRecordId", String.valueOf(id)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].movementType").value("OUT"))
                .andExpect(jsonPath("$.content[0].quantity").value(-2))
                .andExpect(jsonPath("$.content[0].referenceNumber").value(mnt));

        // more than the store holds -> 422, nothing issued; reject it instead
        JsonNode tooMany = postJson("/api/v1/maintenance/" + id + "/parts", new MaintenancePartRequest(filter, null, null, 50, null), 201);
        long tooManyId = tooMany.get("parts").get(2).get("id").asLong();
        mockMvc.perform(post("/api/v1/maintenance/" + id + "/parts/" + tooManyId + "/approve").header("Authorization", adminToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
        mockMvc.perform(get("/api/v1/spare-parts/" + filter).header("Authorization", adminToken))
                .andExpect(jsonPath("$.currentStock").value(8));
        mockMvc.perform(post("/api/v1/maintenance/" + id + "/complete").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(new MaintenanceCompleteRequest("done", null, null, null))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PARTS_PENDING_APPROVAL"));
        postJson("/api/v1/maintenance/" + id + "/parts/" + tooManyId + "/reject", new PartRejectRequest("Only two needed"), 200);

        // waiting for parts and back, task done, comment
        postJson("/api/v1/maintenance/" + id + "/wait-for-parts", null, 200);
        postJson("/api/v1/maintenance/" + id + "/resume", null, 200);
        mockMvc.perform(patch("/api/v1/maintenance/" + id + "/tasks/" + taskId).header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(new MaintenanceTaskStatusRequest(TaskStatus.DONE, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks[0].status").value("DONE"))
                .andExpect(jsonPath("$.tasks[0].completedBy").exists())
                .andExpect(jsonPath("$.tasksDone").value(1));
        postJson("/api/v1/maintenance/" + id + "/comments", new MaintenanceCommentRequest("Filter replaced, running smoothly"), 201);

        // complete: labour 10000 (base) + 20000 (task) = 30000; parts 29000; other 1000 -> 60000
        postJson("/api/v1/maintenance/" + id + "/complete", new MaintenanceCompleteRequest("Oil and filter replaced, gasket resealed", null, new BigDecimal("1000"), 15405L), 200);
        mockMvc.perform(get("/api/v1/maintenance/" + id).header("Authorization", adminToken))
                .andExpect(jsonPath("$.job.status").value("COMPLETED"))
                .andExpect(jsonPath("$.job.laborCost").value(30000.00))
                .andExpect(jsonPath("$.job.partsCost").value(29000.00))
                .andExpect(jsonPath("$.job.otherCost").value(1000.00))
                .andExpect(jsonPath("$.job.totalCost").value(60000.00))
                .andExpect(jsonPath("$.job.paymentStatus").value("UNPAID"))
                .andExpect(jsonPath("$.job.completedAt").exists());
        mockMvc.perform(get("/api/v1/vehicles/" + vehicle.id()).header("Authorization", adminToken))
                .andExpect(jsonPath("$.operationalStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.maintenanceStatus").value("OK"))
                .andExpect(jsonPath("$.odometerKm").value(15405));
        mockMvc.perform(get("/api/v1/vehicles/" + vehicle.id() + "/maintenance/schedules").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].serviceTypeCode").value("ENGINE_OIL"))
                .andExpect(jsonPath("$[0].lastServiceOdometer").value(15405))
                .andExpect(jsonPath("$[0].nextServiceOdometer").value(20405))
                .andExpect(jsonPath("$[0].kmRemaining").value(5000))
                .andExpect(jsonPath("$[0].lastMaintenanceRecordId").value(id))
                .andExpect(jsonPath("$[0].status").value("OK"));

        // payments: partial then paid
        postJson("/api/v1/maintenance/" + id + "/payments", new MaintenancePaymentRequest(new BigDecimal("20000"), "MOBILE_MONEY", "PAY-1", null), 201);
        mockMvc.perform(get("/api/v1/maintenance/" + id).header("Authorization", adminToken))
                .andExpect(jsonPath("$.job.paymentStatus").value("PARTIAL"))
                .andExpect(jsonPath("$.job.amountPaid").value(20000.00))
                .andExpect(jsonPath("$.job.balanceDue").value(40000.00));
        mockMvc.perform(post("/api/v1/maintenance/" + id + "/payments").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(new MaintenancePaymentRequest(new BigDecimal("50000"), "CASH", null, null))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PAYMENT_EXCEEDS_TOTAL"));
        postJson("/api/v1/maintenance/" + id + "/payments", new MaintenancePaymentRequest(new BigDecimal("40000"), "CASH", null, null), 201);

        // gate pass
        postJson("/api/v1/maintenance/" + id + "/release", null, 200);
        mockMvc.perform(get("/api/v1/maintenance/" + id).header("Authorization", adminToken))
                .andExpect(jsonPath("$.job.status").value("RELEASED"))
                .andExpect(jsonPath("$.job.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.job.gatePassNumber").value("GP-" + mnt))
                .andExpect(jsonPath("$.job.releasedAt").exists())
                .andExpect(jsonPath("$.comments[?(@.commentType == 'STATUS_CHANGE')].toStatus",
                        contains("INSPECTION", "APPROVED", "IN_PROGRESS", "WAITING_FOR_PARTS", "IN_PROGRESS", "COMPLETED", "RELEASED")))
                .andExpect(jsonPath("$.comments[?(@.commentType == 'PART_DECISION')]", hasSize(3)))
                .andExpect(jsonPath("$.comments[?(@.commentType == 'NOTE')].body", contains("Filter replaced, running smoothly")));

        // terminal: no further transitions or edits
        mockMvc.perform(post("/api/v1/maintenance/" + id + "/start").header("Authorization", adminToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        mockMvc.perform(post("/api/v1/maintenance/" + id + "/parts").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(new MaintenancePartRequest(null, "Late part", null, 1, BigDecimal.ONE))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MAINTENANCE_NOT_EDITABLE"));

        // reporting endpoints
        mockMvc.perform(get("/api/v1/maintenance").header("Authorization", adminToken).param("q", mnt).param("status", "RELEASED"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].intakeNumber").exists());
        mockMvc.perform(get("/api/v1/vehicles/" + vehicle.id() + "/maintenance").header("Authorization", adminToken))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/v1/maintenance/garage/dashboard").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCounts.RELEASED").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.completedThisMonth").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.averageDaysInGarage").exists());
        mockMvc.perform(get("/api/v1/maintenance/summary").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobCount").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.costByType[?(@.type == 'CORRECTIVE')].jobCount").exists())
                .andExpect(jsonPath("$.topVehiclesByCost[0].totalCost").exists());
        mockMvc.perform(get("/api/v1/audit-logs/entity/MaintenanceRecord/" + id).header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(8)));
    }

    @Test
    @DisplayName("a job in progress can be cancelled, which restores the vehicle; invalid transitions are rejected")
    void cancelAndInvalidTransitions() throws Exception {
        VehicleResponse vehicle = data.vehicle();
        JsonNode created = postJson("/api/v1/maintenance", job(vehicle.id(), null, null, "5000"), 201);
        long id = created.get("job").get("id").asLong();
        mockMvc.perform(get("/api/v1/maintenance/" + id).header("Authorization", adminToken))
                .andExpect(jsonPath("$.job.intakeNumber").doesNotExist())
                .andExpect(jsonPath("$.job.totalCost").value(5000.00));

        mockMvc.perform(post("/api/v1/maintenance/" + id + "/release").header("Authorization", adminToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        mockMvc.perform(post("/api/v1/maintenance/" + id + "/resume").header("Authorization", adminToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        mockMvc.perform(post("/api/v1/maintenance/" + id + "/payments").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(new MaintenancePaymentRequest(BigDecimal.TEN, null, null, null))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MAINTENANCE_NOT_COMPLETED"));

        // REPORTED -> IN_PROGRESS directly (simple MNT flow)
        postJson("/api/v1/maintenance/" + id + "/start", null, 200);
        mockMvc.perform(get("/api/v1/vehicles/" + vehicle.id()).header("Authorization", adminToken))
                .andExpect(jsonPath("$.operationalStatus").value("IN_MAINTENANCE"));

        // a second job on the same vehicle cannot start while the first is in the workshop
        JsonNode second = postJson("/api/v1/maintenance", job(vehicle.id(), null, null, null), 201);
        long secondId = second.get("job").get("id").asLong();
        mockMvc.perform(post("/api/v1/maintenance/" + secondId + "/start").header("Authorization", adminToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VEHICLE_ALREADY_IN_WORKSHOP"));

        postJson("/api/v1/maintenance/" + id + "/cancel", new MaintenanceCancelRequest("Customer withdrew the vehicle"), 200);
        mockMvc.perform(get("/api/v1/maintenance/" + id).header("Authorization", adminToken))
                .andExpect(jsonPath("$.job.status").value("CANCELLED"))
                .andExpect(jsonPath("$.job.cancellationReason").value("Customer withdrew the vehicle"));
        mockMvc.perform(get("/api/v1/vehicles/" + vehicle.id()).header("Authorization", adminToken))
                .andExpect(jsonPath("$.operationalStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.maintenanceStatus").value("OK"));
        mockMvc.perform(post("/api/v1/maintenance/" + id + "/cancel").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(new MaintenanceCancelRequest("again"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    @DisplayName("approval needs MAINTENANCE_APPROVE: a technician can progress a job but not approve it; a viewer only reads")
    void authorization() throws Exception {
        VehicleResponse vehicle = data.vehicle();
        JsonNode created = postJson("/api/v1/maintenance", job(vehicle.id(), null, null, null), 201);
        long id = created.get("job").get("id").asLong();
        String technician = tokenFor(Roles.TECHNICIAN);
        String viewer = tokenFor(Roles.VIEWER);

        mockMvc.perform(post("/api/v1/maintenance/" + id + "/approve").header("Authorization", technician))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/maintenance/" + id + "/comments").header("Authorization", technician)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(new MaintenanceCommentRequest("Looking at it now"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.comments[?(@.commentType == 'NOTE')].authorRole", contains("TECHNICIAN")));
        mockMvc.perform(get("/api/v1/maintenance/" + id).header("Authorization", viewer))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/maintenance").header("Authorization", viewer)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(job(vehicle.id(), null, null, null))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/maintenance").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"vehicleId\":" + vehicle.id() + ",\"complaint\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("preventive schedules compute next service and DUE_SOON / OVERDUE; the refresh rolls them up into the vehicle status")
    void schedules() throws Exception {
        VehicleResponse vehicle = data.vehicle(); // odometer 15000
        long brake = serviceTypeId("BRAKE_INSPECTION"); // default 10000 km / 180 days
        String body = mockMvc.perform(post("/api/v1/maintenance/schedules").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleId\":" + vehicle.id() + ",\"serviceTypeId\":" + brake + ",\"lastServiceOdometer\":5200}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.intervalKm").value(10000))
                .andExpect(jsonPath("$.intervalDays").value(180))
                .andExpect(jsonPath("$.nextServiceOdometer").value(15200))
                .andExpect(jsonPath("$.kmRemaining").value(200))
                .andExpect(jsonPath("$.status").value("DUE_SOON"))
                .andReturn().getResponse().getContentAsString();
        long scheduleId = json.readTree(body).get("id").asLong();
        mockMvc.perform(post("/api/v1/maintenance/schedules").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleId\":" + vehicle.id() + ",\"serviceTypeId\":" + brake + "}"))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/v1/vehicles/" + vehicle.id()).header("Authorization", adminToken))
                .andExpect(jsonPath("$.maintenanceStatus").value("SERVICE_DUE_SOON"));

        mockMvc.perform(put("/api/v1/maintenance/schedules/" + scheduleId).header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleId\":" + vehicle.id() + ",\"serviceTypeId\":" + brake + ",\"intervalKm\":4000,\"lastServiceOdometer\":10000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextServiceOdometer").value(14000))
                .andExpect(jsonPath("$.status").value("OVERDUE"));
        mockMvc.perform(post("/api/v1/maintenance/schedules/refresh").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schedulesChecked").value(greaterThanOrEqualTo(1)));
        mockMvc.perform(get("/api/v1/vehicles/" + vehicle.id()).header("Authorization", adminToken))
                .andExpect(jsonPath("$.maintenanceStatus").value("SERVICE_OVERDUE"));
        mockMvc.perform(get("/api/v1/maintenance/schedules").header("Authorization", adminToken)
                        .param("status", "OVERDUE").param("vehicleId", vehicle.id().toString()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].kmRemaining").value(-1000));
    }
}
