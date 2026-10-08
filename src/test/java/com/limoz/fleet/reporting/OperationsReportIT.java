package com.limoz.fleet.reporting;

import com.limoz.fleet.booking.BookingTestData;
import com.limoz.fleet.booking.service.DispatchService;
import com.limoz.fleet.booking.dto.AssignSlotRequest;
import com.limoz.fleet.booking.dto.BookingResponse;
import com.limoz.fleet.booking.dto.DepartRequest;
import com.limoz.fleet.booking.dto.VoucherResponse;
import com.limoz.fleet.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OperationsReportIT extends AbstractIntegrationTest {

    @Autowired
    BookingTestData bookingData;

    @Autowired
    DispatchService dispatchService;

    @Test
    @DisplayName("trip, deployment and driver utilisation reports include dispatched work; voucher PDF renders; search and timeline see trips")
    void operationalReports() throws Exception {
        var customer = bookingData.customer();
        var vehicle = bookingData.compliantVehicle();
        var driver = bookingData.data().driver();
        LocalDate today = LocalDate.now();
        BookingResponse booking = bookingData.confirmedBooking(customer.id(), 1, today, today.plusDays(1));
        Long slotId = booking.slots().get(0).id();
        dispatchService.assignSlot(slotId, new AssignSlotRequest(vehicle.id(), driver.id(), null, false, null));
        VoucherResponse voucher = dispatchService.depart(slotId, new DepartRequest(null, null, "Huye", null));

        mockMvc.perform(get("/api/v1/reports/trips").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()").value(greaterThanOrEqualTo(1)));
        mockMvc.perform(get("/api/v1/reports/deployment").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[*][0]", hasItem(voucher.voucherNumber())));
        mockMvc.perform(get("/api/v1/reports/driver-utilization").header("Authorization", adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/reports/vehicle-movement").header("Authorization", adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/vouchers/" + voucher.id() + "/pdf").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"));
        mockMvc.perform(get("/api/v1/search").header("Authorization", adminToken).param("q", voucher.voucherNumber()))
                .andExpect(jsonPath("$.groups.vouchers[0].reference").value(voucher.voucherNumber()));
        mockMvc.perform(get("/api/v1/search").header("Authorization", adminToken).param("q", booking.bookingNumber()))
                .andExpect(jsonPath("$.groups.bookings[0].reference").value(booking.bookingNumber()));
        mockMvc.perform(get("/api/v1/vehicles/" + vehicle.id() + "/timeline").header("Authorization", adminToken))
                .andExpect(jsonPath("$[?(@.category=='TRIP')].title", hasItem("Trip started")))
                .andExpect(jsonPath("$[?(@.category=='DEPLOYMENT')]").isNotEmpty());
    }
}
