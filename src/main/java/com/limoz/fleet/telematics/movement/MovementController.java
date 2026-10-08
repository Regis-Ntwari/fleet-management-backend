package com.limoz.fleet.telematics.movement;

import com.limoz.fleet.common.api.PageResponse;
import com.limoz.fleet.telematics.movement.dto.DailyMovementFilter;
import com.limoz.fleet.telematics.movement.dto.DailyMovementResponse;
import com.limoz.fleet.telematics.movement.dto.MovementSummaryResponse;
import com.limoz.fleet.telematics.movement.dto.RecomputeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Movement analysis", description = "Daily distance, driving time, idle time and movement flags per vehicle")
public class MovementController {

    private final DailyMovementService movementService;
    private final Clock clock;

    @GetMapping("/movement/daily")
    @PreAuthorize("hasAuthority('TELEMATICS_READ')")
    @Operation(summary = "Daily summaries (default: yesterday) filtered by date/range, vehicle, category, flag, moved, data source")
    public PageResponse<DailyMovementResponse> daily(@ParameterObject DailyMovementFilter filter,
                                                     @ParameterObject @PageableDefault(size = 20, sort = "summaryDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return movementService.search(filter, pageable);
    }

    @GetMapping("/movement/summary")
    @PreAuthorize("hasAuthority('TELEMATICS_READ')")
    @Operation(summary = "Fleet totals of one day: vehicles moved / not moved, distance, flagged counts per flag")
    public MovementSummaryResponse summary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return movementService.summary(date == null ? LocalDate.now(clock).minusDays(1) : date);
    }

    @PostMapping("/movement/recompute")
    @PreAuthorize("hasAuthority('TELEMATICS_MANAGE')")
    @Operation(summary = "Recompute the daily summaries of every vehicle for a date (default: yesterday)")
    public RecomputeResponse recompute(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return movementService.recompute(date == null ? LocalDate.now(clock).minusDays(1) : date);
    }

    @GetMapping("/vehicles/{vehicleId}/movement")
    @PreAuthorize("hasAuthority('TELEMATICS_READ')")
    @Operation(summary = "Daily movement series of a vehicle (default: last 30 days)")
    public List<DailyMovementResponse> series(@PathVariable Long vehicleId,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return movementService.series(vehicleId, from, to);
    }
}
