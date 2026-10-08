package com.limoz.fleet.vehicle.timeline.controller;

import com.limoz.fleet.vehicle.timeline.domain.TimelineEntry;
import com.limoz.fleet.vehicle.timeline.service.VehicleTimelineService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/vehicles/{id}/timeline")
@RequiredArgsConstructor
@Tag(name = "Vehicles")
public class VehicleTimelineController {

    private final VehicleTimelineService timelineService;

    @GetMapping
    @PreAuthorize("hasAuthority('VEHICLE_READ')")
    @Operation(summary = "Activity timeline of a vehicle (assignments, trips, fuel, maintenance, incidents, documents, status changes)",
            description = "Defaults to the last 30 days, newest first.")
    public List<TimelineEntry> timeline(@PathVariable Long id,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                        @RequestParam(defaultValue = "100") int limit) {
        return timelineService.timeline(id, from, to, Math.min(Math.max(limit, 1), 500));
    }
}
