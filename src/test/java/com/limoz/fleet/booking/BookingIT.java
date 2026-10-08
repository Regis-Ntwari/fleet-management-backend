package com.limoz.fleet.booking;

import com.limoz.fleet.booking.dto.AssignSlotRequest;
import com.limoz.fleet.booking.dto.BookingCancelRequest;
import com.limoz.fleet.booking.dto.BookingRequest;
import com.limoz.fleet.booking.dto.BookingResponse;
import com.limoz.fleet.booking.dto.ExtraChargeRequest;
import com.limoz.fleet.customer.dto.CustomerResponse;
import com.limoz.fleet.driver.dto.DriverResponse;
import com.limoz.fleet.security.Roles;
import com.limoz.fleet.support.AbstractIntegrationTest;
import com.limoz.fleet.vehicle.dto.VehicleResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BookingIT extends AbstractIntegrationTest {

    @Autowired
    BookingTestData data;

    @Autowired
    JdbcTemplate jdbc;

    private final LocalDate start = LocalDate.now().plusDays(10);

    @Test
    @DisplayName("creating a booking prices every line, derives the period and generates one slot per vehicle")
    void createGeneratesSlotsAndTotal() throws Exception {
        CustomerResponse customer = data.customer();
        BookingRequest request = data.request(customer.id(), false, List.of(
                data.line(data.minibusCategoryId(), 2, PricingType.FULL_DAY, start, start.plusDays(2), new BigDecimal("100000")),
                data.line(data.coasterCategoryId(), 1, PricingType.PER_TRIP, start.plusDays(1), start.plusDays(5), new BigDecimal("50000"))));
        mockMvc.perform(post("/api/v1/bookings").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingNumber").value(startsWith("BK-")))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.customer.name").value(customer.name()))
                .andExpect(jsonPath("$.startDate").value(start.toString()))
                .andExpect(jsonPath("$.endDate").value(start.plusDays(5).toString()))
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(jsonPath("$.lines[0].billableUnits").value(3))
                .andExpect(jsonPath("$.lines[0].lineTotal").value(600000.0))
                .andExpect(jsonPath("$.lines[1].billableUnits").value(1))
                .andExpect(jsonPath("$.lines[1].lineTotal").value(50000.0))
                .andExpect(jsonPath("$.totalAmount").value(650000.0))
                .andExpect(jsonPath("$.vehiclesRequested").value(3))
                .andExpect(jsonPath("$.slotsAssigned").value(0))
                .andExpect(jsonPath("$.slots.length()").value(3))
                .andExpect(jsonPath("$.slots[0].slotNumber").value(1))
                .andExpect(jsonPath("$.slots[2].slotNumber").value(3))
                .andExpect(jsonPath("$.slots[2].categoryName").value("Coaster"))
                .andExpect(jsonPath("$.slots[*].status", hasItem("UNASSIGNED")));
    }

    @Test
    @DisplayName("a line ending before it starts and a missing client are rejected")
    void validation() throws Exception {
        CustomerResponse customer = data.customer();
        BookingRequest invalidDates = data.request(customer.id(), false, List.of(
                data.line(data.minibusCategoryId(), 1, PricingType.FULL_DAY, start.plusDays(3), start, BigDecimal.TEN)));
        mockMvc.perform(post("/api/v1/bookings").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(invalidDates)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_LINE_DATES"));
        mockMvc.perform(post("/api/v1/bookings").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lines\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("a draft can be confirmed once; cancelling needs a reason and cancels the open slots")
    void confirmAndCancel() throws Exception {
        CustomerResponse customer = data.customer();
        BookingResponse booking = bookingServiceCreate(data.request(customer.id(), false,
                List.of(data.line(data.minibusCategoryId(), 2, PricingType.FULL_DAY, start, start, BookingTestData.DAY_RATE))));
        mockMvc.perform(post("/api/v1/bookings/" + booking.id() + "/confirm").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.confirmedAt").exists());
        mockMvc.perform(post("/api/v1/bookings/" + booking.id() + "/confirm").header("Authorization", adminToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));

        mockMvc.perform(post("/api/v1/bookings/" + booking.id() + "/cancel").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/bookings/" + booking.id() + "/cancel").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(new BookingCancelRequest("Client postponed the event"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("Client postponed the event"))
                .andExpect(jsonPath("$.slots[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$.slots[1].status").value("CANCELLED"));
        mockMvc.perform(get("/api/v1/audit-logs/entity/Booking/" + booking.id()).header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("CANCEL"));
    }

    @Test
    @DisplayName("lines can be edited while nothing is assigned; once a slot is assigned the lines are locked")
    void updateRefusedAfterAssignment() throws Exception {
        CustomerResponse customer = data.customer();
        BookingResponse booking = data.confirmedBooking(customer.id(), 1, start, start.plusDays(1));
        BookingRequest moreVehicles = data.request(customer.id(), null,
                List.of(data.line(data.minibusCategoryId(), 3, PricingType.FULL_DAY, start, start.plusDays(1), BookingTestData.DAY_RATE)));
        mockMvc.perform(put("/api/v1/bookings/" + booking.id()).header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(moreVehicles)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehiclesRequested").value(3))
                .andExpect(jsonPath("$.totalAmount").value(960000.0))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        VehicleResponse vehicle = data.compliantVehicle();
        DriverResponse driver = data.data().driver();
        BookingResponse updated = bookingGet(booking.id());
        mockMvc.perform(post("/api/v1/bookings/slots/" + updated.slots().getFirst().id() + "/assign").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(new AssignSlotRequest(vehicle.id(), driver.id(), Shift.DAY, false, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"));

        BookingRequest fewerVehicles = data.request(customer.id(), null,
                List.of(data.line(data.minibusCategoryId(), 2, PricingType.FULL_DAY, start, start.plusDays(1), BookingTestData.DAY_RATE)));
        mockMvc.perform(put("/api/v1/bookings/" + booking.id()).header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(fewerVehicles)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BOOKING_SLOTS_ASSIGNED"));

        // header-only changes with identical lines are still allowed
        BookingRequest sameLines = new BookingRequest(customer.id(), null, "New contact", moreVehicles.contactPhone(), null, ServiceType.EVENT,
                moreVehicles.pickupLocation(), moreVehicles.dropoffLocation(), null, null, 20, "RWF", BookingSource.INTERNAL, "Updated notes",
                moreVehicles.lines(), null);
        mockMvc.perform(put("/api/v1/bookings/" + booking.id()).header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(sameLines)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactName").value("New contact"))
                .andExpect(jsonPath("$.serviceType").value("EVENT"))
                .andExpect(jsonPath("$.slotsAssigned").value(1));
    }

    @Test
    @DisplayName("extra charges are added to the total and can be removed again")
    void extraCharges() throws Exception {
        CustomerResponse customer = data.customer();
        BookingResponse booking = data.confirmedBooking(customer.id(), 1, start, start);
        String body = mockMvc.perform(post("/api/v1/bookings/" + booking.id() + "/extra-charges").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(new ExtraChargeRequest("Waiting time - Day 2", new BigDecimal("45000")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.extraCharges.length()").value(1))
                .andExpect(jsonPath("$.extraChargesTotal").value(45000.0))
                .andExpect(jsonPath("$.totalAmount").value(205000.0))
                .andReturn().getResponse().getContentAsString();
        long chargeId = json.readTree(body).get("extraCharges").get(0).get("id").asLong();
        mockMvc.perform(delete("/api/v1/bookings/" + booking.id() + "/extra-charges/" + chargeId).header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extraCharges.length()").value(0))
                .andExpect(jsonPath("$.totalAmount").value(160000.0));
    }

    @Test
    @DisplayName("a booking can only draw down against a live commitment of the same client")
    void commitmentRules() throws Exception {
        CustomerResponse customer = data.customer();
        CustomerResponse other = data.customer();
        long foreign = insertCommitment(other.id(), "ACTIVE");
        long closed = insertCommitment(customer.id(), "CLOSED");
        long live = insertCommitment(customer.id(), "EXPIRING_SOON");
        BookingRequest base = data.request(customer.id(), false,
                List.of(data.line(data.minibusCategoryId(), 1, PricingType.FULL_DAY, start, start, BookingTestData.DAY_RATE)));

        mockMvc.perform(post("/api/v1/bookings").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(withCommitment(base, foreign))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("COMMITMENT_CUSTOMER_MISMATCH"));
        mockMvc.perform(post("/api/v1/bookings").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(withCommitment(base, closed))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("COMMITMENT_NOT_ACTIVE"));
        mockMvc.perform(post("/api/v1/bookings").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(withCommitment(base, 999_999L))))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/bookings").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(withCommitment(base, live))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.commitmentId").value(live))
                .andExpect(jsonPath("$.commitmentReference").value("CMT-T" + live));
    }

    @Test
    @DisplayName("search supports tabs, status filters, text search and pagination; counts follow the tabs")
    void searchAndCounts() throws Exception {
        CustomerResponse customer = data.customer();
        BookingResponse confirmed = data.confirmedBooking(customer.id(), 1, start, start);
        bookingServiceCreate(data.request(customer.id(), false,
                List.of(data.line(data.minibusCategoryId(), 1, PricingType.FULL_DAY, start, start, BookingTestData.DAY_RATE))));

        mockMvc.perform(get("/api/v1/bookings").header("Authorization", adminToken)
                        .param("q", customer.name()).param("readyFor", "DEPLOYMENT").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].bookingNumber").value(confirmed.bookingNumber()))
                .andExpect(jsonPath("$.content[0].vehiclesRequested").value(1));
        mockMvc.perform(get("/api/v1/bookings").header("Authorization", adminToken)
                        .param("customerId", String.valueOf(customer.id())).param("status", "DRAFT").param("status", "CONFIRMED"))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/api/v1/bookings").header("Authorization", adminToken)
                        .param("customerId", String.valueOf(customer.id())).param("from", start.plusDays(1).toString()))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/v1/bookings/counts").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.all").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.readyForDeployment").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.byStatus.DRAFT").value(greaterThanOrEqualTo(1)));
        mockMvc.perform(get("/api/v1/bookings/recent").header("Authorization", adminToken).param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("a VIEWER can read bookings but cannot create or confirm them")
    void viewerIsReadOnly() throws Exception {
        String viewer = tokenFor(Roles.VIEWER);
        CustomerResponse customer = data.customer();
        BookingResponse booking = data.confirmedBooking(customer.id(), 1, start, start);
        mockMvc.perform(get("/api/v1/bookings/" + booking.id()).header("Authorization", viewer))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/bookings").header("Authorization", viewer)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(data.request(customer.id(), false,
                                List.of(data.line(data.minibusCategoryId(), 1, PricingType.FULL_DAY, start, start, BigDecimal.TEN))))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private BookingResponse bookingServiceCreate(BookingRequest request) throws Exception {
        String body = mockMvc.perform(post("/api/v1/bookings").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readValue(body, BookingResponse.class);
    }

    private BookingResponse bookingGet(Long id) throws Exception {
        String body = mockMvc.perform(get("/api/v1/bookings/" + id).header("Authorization", adminToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readValue(body, BookingResponse.class);
    }

    /** Commitments belong to another module; the test seeds rows directly to exercise the reference checks. */
    private long insertCommitment(Long customerId, String status) {
        String reference = "CMT-T" + customerId + "-" + status.charAt(0) + System.nanoTime() % 100_000;
        Long id = jdbc.queryForObject("insert into commitments (reference, customer_id, title, period_start, period_end, contracted_value, status)"
                        + " values (?, ?, ?, ?, ?, ?, ?) returning id", Long.class,
                reference, customerId, "Staff shuttle", LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(6), new BigDecimal("50000000"), status);
        jdbc.update("update commitments set reference = ? where id = ?", "CMT-T" + id, id);
        return id;
    }

    private static BookingRequest withCommitment(BookingRequest r, Long commitmentId) {
        return new BookingRequest(r.customerId(), commitmentId, r.contactName(), r.contactPhone(), r.contactEmail(), r.serviceType(),
                r.pickupLocation(), r.dropoffLocation(), r.pickupTime(), r.returnTime(), r.passengers(), r.currency(), r.source(), r.notes(),
                r.lines(), r.confirm());
    }
}
