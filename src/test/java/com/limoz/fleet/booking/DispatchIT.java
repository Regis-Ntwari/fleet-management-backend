package com.limoz.fleet.booking;

import com.limoz.fleet.booking.dto.AssignSlotRequest;
import com.limoz.fleet.booking.dto.BookingResponse;
import com.limoz.fleet.booking.dto.DepartRequest;
import com.limoz.fleet.booking.dto.ReturnRequest;
import com.limoz.fleet.customer.dto.CustomerResponse;
import com.limoz.fleet.driver.dto.DriverResponse;
import com.limoz.fleet.security.Roles;
import com.limoz.fleet.support.AbstractIntegrationTest;
import com.limoz.fleet.vehicle.VehicleService;
import com.limoz.fleet.vehicle.VehicleStatus;
import com.limoz.fleet.vehicle.dto.VehicleResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DispatchIT extends AbstractIntegrationTest {

    @Autowired
    BookingTestData data;

    @Autowired
    VehicleService vehicleService;

    @Autowired
    TransactionTemplate transactionTemplate;

    private final LocalDate start = LocalDate.now();

    @Test
    @DisplayName("assignment is refused for the wrong category unless the dispatcher overrides it")
    void categoryMismatch() throws Exception {
        CustomerResponse customer = data.customer();
        BookingResponse booking = bookingWithLine(customer.id(), data.coasterCategoryId());
        VehicleResponse minibus = data.compliantVehicle();
        DriverResponse driver = data.data().driver();
        Long slotId = booking.slots().getFirst().id();
        assign(slotId, new AssignSlotRequest(minibus.id(), driver.id(), null, false, null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CATEGORY_MISMATCH"));
        assign(slotId, new AssignSlotRequest(minibus.id(), driver.id(), Shift.NIGHT, true, "Coaster unavailable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.shift").value("NIGHT"))
                .andExpect(jsonPath("$.vehicle.plateNumber").value(minibus.plateNumber()))
                .andExpect(jsonPath("$.driver.fullName").value(driver.fullName()));
    }

    @Test
    @DisplayName("vehicles in maintenance, without valid documents, and drivers with expired licences cannot be assigned")
    void complianceRules() throws Exception {
        CustomerResponse customer = data.customer();
        BookingResponse booking = data.confirmedBooking(customer.id(), 1, start, start.plusDays(1));
        Long slotId = booking.slots().getFirst().id();
        DriverResponse driver = data.data().driver();

        VehicleResponse inWorkshop = data.compliantVehicle();
        transactionTemplate.executeWithoutResult(tx ->
                vehicleService.transition(vehicleService.load(inWorkshop.id()), VehicleStatus.IN_MAINTENANCE, "Gearbox overhaul"));
        assign(slotId, new AssignSlotRequest(inWorkshop.id(), driver.id(), null, false, null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VEHICLE_IN_MAINTENANCE"));

        VehicleResponse undocumented = data.data().vehicle();
        assign(slotId, new AssignSlotRequest(undocumented.id(), driver.id(), null, false, null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VEHICLE_DOCUMENTS_INVALID"));

        VehicleResponse compliant = data.compliantVehicle();
        DriverResponse expired = data.data().driverWithExpiredLicense();
        assign(slotId, new AssignSlotRequest(compliant.id(), expired.id(), null, false, null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DRIVER_LICENSE_EXPIRED"));
    }

    @Test
    @DisplayName("a vehicle or driver already held by another booking on overlapping dates is rejected")
    void overlappingSlotsRejected() throws Exception {
        CustomerResponse customer = data.customer();
        VehicleResponse vehicle = data.compliantVehicle();
        DriverResponse driver = data.data().driver();
        BookingResponse first = data.confirmedBooking(customer.id(), 1, start, start.plusDays(2));
        assign(first.slots().getFirst().id(), new AssignSlotRequest(vehicle.id(), driver.id(), null, false, null))
                .andExpect(status().isOk());

        BookingResponse second = data.confirmedBooking(customer.id(), 1, start.plusDays(2), start.plusDays(4));
        Long slotId = second.slots().getFirst().id();
        assign(slotId, new AssignSlotRequest(vehicle.id(), data.data().driver().id(), null, false, null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VEHICLE_DOUBLE_BOOKED"));
        assign(slotId, new AssignSlotRequest(data.compliantVehicle().id(), driver.id(), null, false, null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DRIVER_DOUBLE_BOOKED"));

        // the day after the first booking ends is free again
        BookingResponse third = data.confirmedBooking(customer.id(), 1, start.plusDays(3), start.plusDays(4));
        assign(third.slots().getFirst().id(), new AssignSlotRequest(vehicle.id(), driver.id(), null, false, null))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("slots of draft bookings cannot be dispatched and a VIEWER cannot assign")
    void authorisationAndBookingState() throws Exception {
        CustomerResponse customer = data.customer();
        VehicleResponse vehicle = data.compliantVehicle();
        DriverResponse driver = data.data().driver();
        BookingResponse draft = draftBooking(customer.id());
        assign(draft.slots().getFirst().id(), new AssignSlotRequest(vehicle.id(), driver.id(), null, false, null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_DISPATCHABLE"));
        mockMvc.perform(post("/api/v1/bookings/slots/" + draft.slots().getFirst().id() + "/assign").header("Authorization", tokenFor(Roles.VIEWER))
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(new AssignSlotRequest(vehicle.id(), driver.id(), null, false, null))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/bookings/slots/" + draft.slots().getFirst().id() + "/depart").header("Authorization", adminToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_DISPATCHABLE"));
    }

    @Test
    @DisplayName("departure opens a trip and a voucher; the return closes the trip and prices the voucher")
    void departAndReturn() throws Exception {
        CustomerResponse customer = data.customer();
        VehicleResponse vehicle = data.compliantVehicle();
        DriverResponse driver = data.data().driver();
        BookingResponse booking = data.confirmedBooking(customer.id(), 1, start, start.plusDays(2));
        Long slotId = booking.slots().getFirst().id();
        assign(slotId, new AssignSlotRequest(vehicle.id(), driver.id(), null, false, null)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/bookings/" + booking.id()).header("Authorization", adminToken))
                .andExpect(jsonPath("$.status").value("READY_FOR_DEPLOYMENT"));

        Instant departedAt = Instant.now().minus(Duration.ofDays(3)).truncatedTo(ChronoUnit.SECONDS);
        String voucherJson = mockMvc.perform(post("/api/v1/bookings/slots/" + slotId + "/depart").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(new DepartRequest(15100L, departedAt, "Musanze volcanoes", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voucherNumber").value(startsWith("LIMOZ/")))
                .andExpect(jsonPath("$.status").value("ONGOING"))
                .andExpect(jsonPath("$.startKm").value(15100))
                .andExpect(jsonPath("$.plannedDays").value(3))
                .andExpect(jsonPath("$.dayRate").value(160000.0))
                .andExpect(jsonPath("$.destination").value("Musanze volcanoes"))
                .andExpect(jsonPath("$.clientTel").value("+250788111222"))
                .andExpect(jsonPath("$.tripId").exists())
                .andReturn().getResponse().getContentAsString();
        long voucherId = json.readTree(voucherJson).get("id").asLong();
        long tripId = json.readTree(voucherJson).get("tripId").asLong();

        mockMvc.perform(get("/api/v1/vehicles/" + vehicle.id()).header("Authorization", adminToken))
                .andExpect(jsonPath("$.operationalStatus").value("ON_TRIP"))
                .andExpect(jsonPath("$.odometerKm").value(15100));
        mockMvc.perform(get("/api/v1/drivers/" + driver.id()).header("Authorization", adminToken))
                .andExpect(jsonPath("$.status").value("ON_TRIP"));
        mockMvc.perform(get("/api/v1/trips/" + tripId).header("Authorization", adminToken))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.bookingNumber").value(booking.bookingNumber()))
                .andExpect(jsonPath("$.bookingSlotId").value(slotId))
                .andExpect(jsonPath("$.startOdometerKm").value(15100))
                .andExpect(jsonPath("$.origin").value("Kigali HQ"));
        mockMvc.perform(get("/api/v1/bookings/" + booking.id()).header("Authorization", adminToken))
                .andExpect(jsonPath("$.status").value("DEPLOYED"))
                .andExpect(jsonPath("$.deployedAt").exists())
                .andExpect(jsonPath("$.slots[0].status").value("DEPLOYED"))
                .andExpect(jsonPath("$.vouchers.length()").value(1));
        mockMvc.perform(post("/api/v1/bookings/slots/" + slotId + "/depart").header("Authorization", adminToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SLOT_ALREADY_DEPARTED"));

        // end odometer below the start reading is refused
        mockMvc.perform(post("/api/v1/bookings/slots/" + slotId + "/return").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ReturnRequest(15000L, null, null, null, null, null, null, null))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("END_ODOMETER_BELOW_START"));

        Instant returnedAt = departedAt.plus(Duration.ofHours(60));
        mockMvc.perform(post("/api/v1/bookings/slots/" + slotId + "/return").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ReturnRequest(15450L, returnedAt, new BigDecimal("35000"), new BigDecimal("20000"),
                                new BigDecimal("100000"), null, "Returned on time", "Clean"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETURNED"))
                .andExpect(jsonPath("$.endKm").value(15450))
                .andExpect(jsonPath("$.distanceKm").value(350))
                .andExpect(jsonPath("$.effectiveDays").value(2.5))
                .andExpect(jsonPath("$.institutionAmount").value(400000.0))
                .andExpect(jsonPath("$.ownerAmount").value(100000.0))
                .andExpect(jsonPath("$.fuelAmount").value(35000.0))
                .andExpect(jsonPath("$.netAmount").value(65000.0))
                .andExpect(jsonPath("$.missionDueAmount").value(20000.0))
                .andExpect(jsonPath("$.comment").value("Returned on time"));

        mockMvc.perform(get("/api/v1/trips/" + tripId).header("Authorization", adminToken))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.endOdometerKm").value(15450))
                .andExpect(jsonPath("$.distanceKm").value(350.0))
                .andExpect(jsonPath("$.durationMinutes").value(3600));
        mockMvc.perform(get("/api/v1/vehicles/" + vehicle.id()).header("Authorization", adminToken))
                .andExpect(jsonPath("$.operationalStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.odometerKm").value(15450));
        mockMvc.perform(get("/api/v1/drivers/" + driver.id()).header("Authorization", adminToken))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
        mockMvc.perform(get("/api/v1/bookings/" + booking.id()).header("Authorization", adminToken))
                .andExpect(jsonPath("$.status").value("READY_FOR_BILLING"))
                .andExpect(jsonPath("$.slots[0].status").value("RETURNED"))
                .andExpect(jsonPath("$.slots[0].odometerIn").value(15450));

        // vouchers are searchable and editable; the booking can then be completed
        mockMvc.perform(get("/api/v1/vouchers").header("Authorization", adminToken)
                        .param("bookingId", String.valueOf(booking.id())).param("status", "RETURNED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(voucherId));
        mockMvc.perform(patch("/api/v1/vouchers/" + voucherId).header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"poAmount\":500000,\"ownerAmount\":120000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.poAmount").value(500000.0))
                .andExpect(jsonPath("$.netAmount").value(85000.0));
        mockMvc.perform(post("/api/v1/bookings/" + booking.id() + "/complete").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").exists());
    }

    @Test
    @DisplayName("booking status follows its slots: partially assigned stays CONFIRMED, fully assigned is READY_FOR_DEPLOYMENT")
    void bookingStatusDerivation() throws Exception {
        CustomerResponse customer = data.customer();
        BookingResponse booking = data.confirmedBooking(customer.id(), 2, start.plusDays(20), start.plusDays(21));
        Long slot1 = booking.slots().get(0).id();
        Long slot2 = booking.slots().get(1).id();
        VehicleResponse firstVehicle = data.compliantVehicle();
        assign(slot1, new AssignSlotRequest(firstVehicle.id(), data.data().driver().id(), null, false, null)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/bookings/" + booking.id()).header("Authorization", adminToken))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.slotsAssigned").value(1));
        assign(slot2, new AssignSlotRequest(data.compliantVehicle().id(), data.data().driver().id(), null, false, null)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/bookings/" + booking.id()).header("Authorization", adminToken))
                .andExpect(jsonPath("$.status").value("READY_FOR_DEPLOYMENT"))
                .andExpect(jsonPath("$.slotsAssigned").value(2));
        mockMvc.perform(post("/api/v1/bookings/slots/" + slot2 + "/unassign").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNASSIGNED"))
                .andExpect(jsonPath("$.vehicle").doesNotExist());
        mockMvc.perform(get("/api/v1/bookings/" + booking.id()).header("Authorization", adminToken))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        // cancelling releases the held vehicle: it can be assigned elsewhere on the same dates afterwards
        mockMvc.perform(post("/api/v1/bookings/" + booking.id() + "/cancel").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Event cancelled\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        BookingResponse other = data.confirmedBooking(customer.id(), 1, start.plusDays(20), start.plusDays(21));
        assign(other.slots().getFirst().id(), new AssignSlotRequest(firstVehicle.id(), data.data().driver().id(), null, false, null))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions assign(Long slotId, AssignSlotRequest request) throws Exception {
        return mockMvc.perform(post("/api/v1/bookings/slots/" + slotId + "/assign").header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(request)));
    }

    private BookingResponse bookingWithLine(Long customerId, Long categoryId) throws Exception {
        return create(data.request(customerId, true, List.of(data.line(categoryId, 1, PricingType.FULL_DAY, start, start.plusDays(1), BookingTestData.DAY_RATE))));
    }

    private BookingResponse draftBooking(Long customerId) throws Exception {
        return create(data.request(customerId, false, List.of(data.line(data.minibusCategoryId(), 1, PricingType.FULL_DAY, start, start, BookingTestData.DAY_RATE))));
    }

    private BookingResponse create(Object request) throws Exception {
        String body = mockMvc.perform(post("/api/v1/bookings").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(request)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readValue(body, BookingResponse.class);
    }
}
