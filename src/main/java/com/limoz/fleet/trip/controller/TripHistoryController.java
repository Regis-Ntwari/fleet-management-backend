package com.limoz.fleet.trip.controller;

import com.limoz.fleet.trip.domain.Trip;
import com.limoz.fleet.trip.service.TripService;

import com.limoz.fleet.common.api.PageResponse;
import com.limoz.fleet.trip.dto.TripResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Trip history exposed under the vehicle and driver resources. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Trips")
public class TripHistoryController {

    private final TripService tripService;

    @GetMapping("/vehicles/{vehicleId}/trips")
    @PreAuthorize("hasAuthority('TRIP_READ')")
    @Operation(summary = "Trip history of a vehicle (latest first)")
    public PageResponse<TripResponse> forVehicle(@PathVariable Long vehicleId, @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return tripService.forVehicle(vehicleId, pageable);
    }

    @GetMapping("/drivers/{driverId}/trips")
    @PreAuthorize("hasAuthority('TRIP_READ')")
    @Operation(summary = "Trip history of a driver (latest first)")
    public PageResponse<TripResponse> forDriver(@PathVariable Long driverId, @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return tripService.forDriver(driverId, pageable);
    }
}
