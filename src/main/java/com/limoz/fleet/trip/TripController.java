package com.limoz.fleet.trip;

import com.limoz.fleet.common.api.PageResponse;
import com.limoz.fleet.trip.dto.TripCancelRequest;
import com.limoz.fleet.trip.dto.TripCompleteRequest;
import com.limoz.fleet.trip.dto.TripFilter;
import com.limoz.fleet.trip.dto.TripRequest;
import com.limoz.fleet.trip.dto.TripResponse;
import com.limoz.fleet.trip.dto.TripStartRequest;
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
@RequestMapping("/api/v1/trips")
@RequiredArgsConstructor
@Tag(name = "Trips", description = "Vehicle movements: planning, start / completion with odometer readings, history")
public class TripController {

    private final TripService tripService;

    @GetMapping
    @PreAuthorize("hasAuthority('TRIP_READ')")
    @Operation(summary = "Search trips (paginated)",
            description = "Example: `?q=Musanze&status=PLANNED&status=IN_PROGRESS&vehicleId=3&from=2026-06-01&to=2026-06-30`")
    public PageResponse<TripResponse> search(@ParameterObject TripFilter filter,
                                             @ParameterObject @PageableDefault(size = 20, sort = "scheduledStartAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return tripService.search(filter, pageable);
    }

    @GetMapping("/active")
    @PreAuthorize("hasAuthority('TRIP_READ')")
    @Operation(summary = "Trips currently in progress")
    public List<TripResponse> active() {
        return tripService.active();
    }

    @GetMapping("/mine")
    @PreAuthorize("hasAuthority('TRIP_READ')")
    @Operation(summary = "Trips of the driver profile linked to the current user")
    public PageResponse<TripResponse> mine(@RequestParam(required = false) List<TripStatus> status,
                                           @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return tripService.mine(status, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('TRIP_READ')")
    public TripResponse get(@PathVariable Long id) {
        return tripService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('TRIP_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Plan a trip", description = "Validates vehicle documents, driver licence and overlapping trips in the scheduled window.")
    public TripResponse create(@Valid @RequestBody TripRequest request) {
        return tripService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('TRIP_MANAGE')")
    @Operation(summary = "Update a planned trip")
    public TripResponse update(@PathVariable Long id, @Valid @RequestBody TripRequest request) {
        return tripService.update(id, request);
    }

    @PostMapping("/{id}/dispatch")
    @PreAuthorize("hasAuthority('TRIP_MANAGE')")
    @Operation(summary = "Mark a planned trip as dispatched to its crew")
    public TripResponse dispatch(@PathVariable Long id) {
        return tripService.dispatch(id);
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('TRIP_MANAGE')")
    @Operation(summary = "Start a trip (records the start odometer, vehicle and driver go ON_TRIP)")
    public TripResponse start(@PathVariable Long id, @Valid @RequestBody(required = false) TripStartRequest request) {
        return tripService.start(id, request == null ? new TripStartRequest(null, null) : request);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('TRIP_MANAGE')")
    @Operation(summary = "Complete a trip (end odometer >= start; distance and duration are computed)")
    public TripResponse complete(@PathVariable Long id, @Valid @RequestBody TripCompleteRequest request) {
        return tripService.complete(id, request);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('TRIP_MANAGE')")
    @Operation(summary = "Cancel a trip that has not been completed")
    public TripResponse cancel(@PathVariable Long id, @Valid @RequestBody TripCancelRequest request) {
        return tripService.cancel(id, request);
    }
}
