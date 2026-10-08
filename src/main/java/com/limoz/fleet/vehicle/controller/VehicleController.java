package com.limoz.fleet.vehicle.controller;

import com.limoz.fleet.vehicle.domain.Vehicle;
import com.limoz.fleet.vehicle.domain.VehicleStatus;
import com.limoz.fleet.vehicle.service.VehicleService;

import com.limoz.fleet.common.api.PageResponse;
import com.limoz.fleet.vehicle.dto.OdometerCorrectionRequest;
import com.limoz.fleet.vehicle.dto.OdometerLogResponse;
import com.limoz.fleet.vehicle.dto.VehicleFilter;
import com.limoz.fleet.vehicle.dto.VehicleRequest;
import com.limoz.fleet.vehicle.dto.VehicleResponse;
import com.limoz.fleet.vehicle.dto.VehicleStatusChangeRequest;
import com.limoz.fleet.vehicle.dto.VehicleSummary;
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
@RequestMapping("/api/v1/vehicles")
@RequiredArgsConstructor
@Tag(name = "Vehicles", description = "Fleet vehicle master data, status and odometer history")
public class VehicleController {

    private final VehicleService vehicleService;

    @GetMapping
    @PreAuthorize("hasAuthority('VEHICLE_READ')")
    @Operation(summary = "Search vehicles (server-side paging, filtering and sorting)",
            description = "Example: `?q=RAD&status=AVAILABLE&status=ASSIGNED&categoryId=3&page=0&size=20&sort=plateNumber,asc`")
    public PageResponse<VehicleResponse> search(@ParameterObject VehicleFilter filter,
                                                @ParameterObject @PageableDefault(size = 20, sort = "plateNumber", direction = Sort.Direction.ASC) Pageable pageable) {
        return vehicleService.search(filter, pageable);
    }

    @GetMapping("/summaries")
    @PreAuthorize("hasAuthority('VEHICLE_READ')")
    @Operation(summary = "Lightweight list for dropdowns (optionally filtered by status)")
    public List<VehicleSummary> summaries(@RequestParam(required = false) List<VehicleStatus> status) {
        return vehicleService.summaries(status);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('VEHICLE_READ')")
    @Operation(summary = "Vehicle profile")
    public VehicleResponse get(@PathVariable Long id) {
        return vehicleService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('VEHICLE_CREATE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a vehicle")
    public VehicleResponse create(@Valid @RequestBody VehicleRequest request) {
        return vehicleService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('VEHICLE_UPDATE')")
    @Operation(summary = "Update a vehicle profile")
    public VehicleResponse update(@PathVariable Long id, @Valid @RequestBody VehicleRequest request) {
        return vehicleService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('VEHICLE_UPDATE')")
    @Operation(summary = "Manually change operational status (AVAILABLE, RESERVED, OUT_OF_SERVICE, INACTIVE)")
    public VehicleResponse changeStatus(@PathVariable Long id, @Valid @RequestBody VehicleStatusChangeRequest request) {
        return vehicleService.changeStatus(id, request);
    }

    @PostMapping("/{id}/odometer/correct")
    @PreAuthorize("hasAuthority('VEHICLE_ODOMETER_CORRECT')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Administrator-approved odometer correction (the only way to lower a reading)")
    public void correctOdometer(@PathVariable Long id, @Valid @RequestBody OdometerCorrectionRequest request) {
        vehicleService.correctOdometer(id, request);
    }

    @GetMapping("/{id}/odometer")
    @PreAuthorize("hasAuthority('VEHICLE_READ')")
    @Operation(summary = "Odometer history")
    public PageResponse<OdometerLogResponse> odometer(@PathVariable Long id,
                                                      @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return vehicleService.odometerHistory(id, pageable);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('VEHICLE_DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Archive a vehicle (soft delete - history is retained)")
    public void archive(@PathVariable Long id) {
        vehicleService.archive(id);
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAuthority('VEHICLE_DELETE')")
    @Operation(summary = "Restore an archived vehicle")
    public VehicleResponse restore(@PathVariable Long id) {
        return vehicleService.restore(id);
    }
}
