package com.limoz.fleet.booking.controller;

import com.limoz.fleet.booking.domain.Booking;
import com.limoz.fleet.booking.service.BookingService;

import com.limoz.fleet.booking.dto.BookingCancelRequest;
import com.limoz.fleet.booking.dto.BookingCounts;
import com.limoz.fleet.booking.dto.BookingFilter;
import com.limoz.fleet.booking.dto.BookingRequest;
import com.limoz.fleet.booking.dto.BookingResponse;
import com.limoz.fleet.booking.dto.BookingSlotResponse;
import com.limoz.fleet.booking.dto.BookingSummary;
import com.limoz.fleet.booking.dto.ExtraChargeRequest;
import com.limoz.fleet.common.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
@Tag(name = "Bookings", description = "Client bookings: lines, pricing, deployment slots and lifecycle")
public class BookingController {

    private final BookingService bookingService;

    @GetMapping
    @PreAuthorize("hasAuthority('BOOKING_READ')")
    @Operation(summary = "Search bookings (paginated)",
            description = "Example: `?q=MTN&status=CONFIRMED&readyFor=DEPLOYMENT&from=2026-06-01&to=2026-06-30&sort=startDate,desc`")
    public PageResponse<BookingSummary> search(@ParameterObject BookingFilter filter,
                                               @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return bookingService.search(filter, pageable);
    }

    @GetMapping("/counts")
    @PreAuthorize("hasAuthority('BOOKING_READ')")
    @Operation(summary = "Tab counters (all / ready for deployment / ready for billing) and totals per status")
    public BookingCounts counts() {
        return bookingService.counts();
    }

    @GetMapping("/recent")
    @PreAuthorize("hasAuthority('BOOKING_READ')")
    @Operation(summary = "Most recently created bookings")
    public List<BookingSummary> recent(@RequestParam(defaultValue = "5") int limit) {
        return bookingService.recent(limit);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('BOOKING_READ')")
    @Operation(summary = "Booking detail with lines, slots, extra charges and vouchers")
    public BookingResponse get(@PathVariable Long id) {
        return bookingService.get(id);
    }

    @GetMapping("/{id}/slots")
    @PreAuthorize("hasAuthority('BOOKING_READ')")
    @Operation(summary = "Deployment slots of a booking")
    public List<BookingSlotResponse> slots(@PathVariable Long id) {
        return bookingService.slots(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('BOOKING_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a booking (draft, or confirmed when confirm=true)")
    public BookingResponse create(@Valid @RequestBody BookingRequest request) {
        return bookingService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('BOOKING_MANAGE')")
    @Operation(summary = "Update an open booking (lines are regenerated when they change)")
    public BookingResponse update(@PathVariable Long id, @Valid @RequestBody BookingRequest request) {
        return bookingService.update(id, request);
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAuthority('BOOKING_MANAGE')")
    @Operation(summary = "Confirm a draft / requested booking")
    public BookingResponse confirm(@PathVariable Long id) {
        return bookingService.confirm(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('BOOKING_MANAGE')")
    @Operation(summary = "Cancel a booking and release its held vehicles")
    public BookingResponse cancel(@PathVariable Long id, @Valid @RequestBody BookingCancelRequest request) {
        return bookingService.cancel(id, request);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('BOOKING_MANAGE')")
    @Operation(summary = "Close a booking that is ready for billing")
    public BookingResponse complete(@PathVariable Long id) {
        return bookingService.complete(id);
    }

    @PostMapping("/{id}/extra-charges")
    @PreAuthorize("hasAuthority('BOOKING_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add an extra charge (waiting time, extra mileage...)")
    public BookingResponse addExtraCharge(@PathVariable Long id, @Valid @RequestBody ExtraChargeRequest request) {
        return bookingService.addExtraCharge(id, request);
    }

    @DeleteMapping("/{id}/extra-charges/{chargeId}")
    @PreAuthorize("hasAuthority('BOOKING_MANAGE')")
    @Operation(summary = "Remove an extra charge")
    public BookingResponse removeExtraCharge(@PathVariable Long id, @PathVariable Long chargeId) {
        return bookingService.removeExtraCharge(id, chargeId);
    }
}
