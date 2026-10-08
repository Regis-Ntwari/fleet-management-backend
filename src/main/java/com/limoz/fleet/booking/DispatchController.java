package com.limoz.fleet.booking;

import com.limoz.fleet.booking.dto.AvailabilityResponse;
import com.limoz.fleet.booking.dto.DispatchBoardResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/dispatch")
@RequiredArgsConstructor
@Tag(name = "Dispatch")
public class DispatchController {

    private final DispatchBoardService boardService;

    @GetMapping("/board")
    @PreAuthorize("hasAuthority('BOOKING_READ')")
    @Operation(summary = "Dispatcher board for a day (defaults to today)",
            description = "Upcoming jobs (unassigned first), today's departures and expected returns, trips in progress, available vehicles and drivers.")
    public DispatchBoardResponse board(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return boardService.board(date);
    }

    @GetMapping("/availability")
    @PreAuthorize("hasAuthority('BOOKING_READ')")
    @Operation(summary = "Vehicle availability calendar",
            description = "Per vehicle, the spans in [from, to] during which it is reserved, deployed, on a trip, in maintenance or out of service. Defaults to the next 14 days.")
    public AvailabilityResponse availability(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                             @RequestParam(required = false) Long categoryId) {
        return boardService.availability(from, to, categoryId);
    }
}
