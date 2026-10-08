package com.limoz.fleet.booking;

import com.limoz.fleet.booking.dto.AssignSlotRequest;
import com.limoz.fleet.booking.dto.BookingResponse;
import com.limoz.fleet.customer.dto.CustomerResponse;
import com.limoz.fleet.driver.dto.DriverResponse;
import com.limoz.fleet.support.AbstractIntegrationTest;
import com.limoz.fleet.vehicle.dto.VehicleResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDate;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DispatchBoardIT extends AbstractIntegrationTest {

    @Autowired
    BookingTestData data;

    private final LocalDate today = LocalDate.now();

    @Test
    @DisplayName("the board lists upcoming jobs (unassigned first), today's departures and returns, and free vehicles / drivers")
    void board() throws Exception {
        CustomerResponse customer = data.customer();
        VehicleResponse vehicle = data.compliantVehicle();
        VehicleResponse spare = data.compliantVehicle();
        DriverResponse driver = data.data().driver();
        BookingResponse booking = data.confirmedBooking(customer.id(), 2, today, today.plusDays(1));
        Long slot1 = booking.slots().get(0).id();
        mockMvc.perform(post("/api/v1/bookings/slots/" + slot1 + "/assign").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(new AssignSlotRequest(vehicle.id(), driver.id(), null, false, null))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/dispatch/board").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(today.toString()))
                .andExpect(jsonPath("$.counts.upcomingJobs").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.counts.unassignedJobs").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.upcomingJobs[0].status").value("UNASSIGNED"))
                .andExpect(jsonPath("$.upcomingJobs[?(@.bookingId == " + booking.id() + ")]", hasSize(2)))
                .andExpect(jsonPath("$.departuresToday[?(@.id == " + slot1 + ")].vehicle.plateNumber").value(vehicle.plateNumber()))
                .andExpect(jsonPath("$.assignedToday[?(@.id == " + slot1 + ")].driver.id").value(driver.id().intValue()))
                .andExpect(jsonPath("$.availableVehicles[*].id", not(hasItem(vehicle.id().intValue()))))
                .andExpect(jsonPath("$.availableVehicles[*].id", hasItem(spare.id().intValue())))
                .andExpect(jsonPath("$.availableDrivers[*].id", not(hasItem(driver.id().intValue()))))
                .andExpect(jsonPath("$.currentTrips").isArray())
                .andExpect(jsonPath("$.expectedReturnsToday").isArray());

        mockMvc.perform(post("/api/v1/bookings/slots/" + slot1 + "/depart").header("Authorization", adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/dispatch/board").header("Authorization", adminToken).param("date", today.plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expectedReturnsToday[?(@.id == " + slot1 + ")].status").value("DEPLOYED"))
                .andExpect(jsonPath("$.currentTrips[?(@.bookingSlotId == " + slot1 + ")].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.counts.currentTrips").value(greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("the availability calendar reports reserved, deployed and out-of-service spans per vehicle")
    void availability() throws Exception {
        CustomerResponse customer = data.customer();
        VehicleResponse vehicle = data.compliantVehicle();
        VehicleResponse free = data.compliantVehicle();
        DriverResponse driver = data.data().driver();
        LocalDate from = today.plusDays(30);
        BookingResponse booking = data.confirmedBooking(customer.id(), 1, from.plusDays(2), from.plusDays(20));
        mockMvc.perform(post("/api/v1/bookings/slots/" + booking.slots().getFirst().id() + "/assign").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(new AssignSlotRequest(vehicle.id(), driver.id(), null, false, null))))
                .andExpect(status().isOk());
        String path = "$.rows[?(@.vehicle.id == " + vehicle.id() + ")]";
        mockMvc.perform(get("/api/v1/dispatch/availability").header("Authorization", adminToken)
                        .param("from", from.toString()).param("to", from.plusDays(13).toString()).param("categoryId", String.valueOf(data.minibusCategoryId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value(from.toString()))
                .andExpect(jsonPath("$.to").value(from.plusDays(13).toString()))
                .andExpect(jsonPath(path + ".fullyAvailable").value(false))
                .andExpect(jsonPath(path + ".spans[0].type").value("RESERVED"))
                .andExpect(jsonPath(path + ".spans[0].reason").value("Reserved · " + customer.name()))
                .andExpect(jsonPath(path + ".spans[0].from").value(from.plusDays(2).toString()))
                .andExpect(jsonPath(path + ".spans[0].to").value(from.plusDays(13).toString()))
                .andExpect(jsonPath(path + ".spans[0].reference").value(booking.bookingNumber()))
                .andExpect(jsonPath("$.rows[?(@.vehicle.id == " + free.id() + ")].fullyAvailable").value(true));

        mockMvc.perform(get("/api/v1/dispatch/availability").header("Authorization", adminToken)
                        .param("from", from.plusDays(13).toString()).param("to", from.toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
    }
}
