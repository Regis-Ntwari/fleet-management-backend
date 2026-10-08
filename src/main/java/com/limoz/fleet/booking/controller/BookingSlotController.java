package com.limoz.fleet.booking.controller;

import com.limoz.fleet.booking.service.DispatchService;

import com.limoz.fleet.booking.dto.AssignSlotRequest;
import com.limoz.fleet.booking.dto.BookingSlotResponse;
import com.limoz.fleet.booking.dto.DepartRequest;
import com.limoz.fleet.booking.dto.ReturnRequest;
import com.limoz.fleet.booking.dto.VoucherResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/bookings/slots")
@RequiredArgsConstructor
@Tag(name = "Dispatch", description = "Slot assignment, departures and returns")
public class BookingSlotController {

    private final DispatchService dispatchService;

    @GetMapping("/{slotId}")
    @PreAuthorize("hasAuthority('BOOKING_READ')")
    public BookingSlotResponse get(@PathVariable Long slotId) {
        return dispatchService.getSlot(slotId);
    }

    @PostMapping("/{slotId}/assign")
    @PreAuthorize("hasAuthority('DISPATCH_MANAGE')")
    @Operation(summary = "Assign or replace the vehicle and driver of a slot",
            description = "Checks vehicle status, category, documents, driver status and licence, and overlapping slots / trips.")
    public BookingSlotResponse assign(@PathVariable Long slotId, @Valid @RequestBody AssignSlotRequest request) {
        return dispatchService.assignSlot(slotId, request);
    }

    @PostMapping("/{slotId}/unassign")
    @PreAuthorize("hasAuthority('DISPATCH_MANAGE')")
    @Operation(summary = "Release the vehicle and driver of an assigned slot")
    public BookingSlotResponse unassign(@PathVariable Long slotId) {
        return dispatchService.unassignSlot(slotId);
    }

    @PostMapping("/{slotId}/depart")
    @PreAuthorize("hasAuthority('DISPATCH_MANAGE')")
    @Operation(summary = "Record the departure: opens a trip and issues the deployment voucher")
    public VoucherResponse depart(@PathVariable Long slotId, @Valid @RequestBody(required = false) DepartRequest request) {
        return dispatchService.depart(slotId, request == null ? new DepartRequest(null, null, null, null) : request);
    }

    @PostMapping("/{slotId}/return")
    @PreAuthorize("hasAuthority('DISPATCH_MANAGE')")
    @Operation(summary = "Record the return: completes the trip and prices the voucher")
    public VoucherResponse recordReturn(@PathVariable Long slotId, @Valid @RequestBody ReturnRequest request) {
        return dispatchService.recordReturn(slotId, request);
    }
}
