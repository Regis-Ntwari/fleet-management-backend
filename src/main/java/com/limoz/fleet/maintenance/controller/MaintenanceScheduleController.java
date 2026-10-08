package com.limoz.fleet.maintenance.controller;

import com.limoz.fleet.maintenance.service.MaintenanceScheduleService;

import com.limoz.fleet.common.api.PageResponse;
import com.limoz.fleet.maintenance.dto.ScheduleFilter;
import com.limoz.fleet.maintenance.dto.ScheduleRefreshResponse;
import com.limoz.fleet.maintenance.dto.ScheduleRequest;
import com.limoz.fleet.maintenance.dto.ScheduleResponse;
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
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Maintenance Schedules", description = "Preventive maintenance schedules per vehicle and service type (OK / DUE_SOON / OVERDUE)")
public class MaintenanceScheduleController {

    private final MaintenanceScheduleService scheduleService;

    @GetMapping("/maintenance/schedules")
    @PreAuthorize("hasAuthority('MAINTENANCE_READ')")
    @Operation(summary = "Upcoming / overdue schedules with km and days remaining", description = "Example: `?status=DUE_SOON&status=OVERDUE&vehicleId=3`")
    public PageResponse<ScheduleResponse> search(@ParameterObject ScheduleFilter filter,
                                                 @ParameterObject @PageableDefault(size = 20, sort = "nextServiceDate", direction = Sort.Direction.ASC) Pageable pageable) {
        return scheduleService.search(filter, pageable);
    }

    @GetMapping("/maintenance/schedules/{id}")
    @PreAuthorize("hasAuthority('MAINTENANCE_READ')")
    public ScheduleResponse get(@PathVariable Long id) {
        return scheduleService.get(id);
    }

    @GetMapping("/vehicles/{vehicleId}/maintenance/schedules")
    @PreAuthorize("hasAuthority('MAINTENANCE_READ')")
    @Operation(summary = "Preventive schedules of a vehicle")
    public List<ScheduleResponse> forVehicle(@PathVariable Long vehicleId) {
        return scheduleService.forVehicle(vehicleId);
    }

    @PostMapping("/maintenance/schedules")
    @PreAuthorize("hasAuthority('MAINTENANCE_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a schedule for a vehicle and service type (intervals default from the service type / settings)")
    public ScheduleResponse create(@Valid @RequestBody ScheduleRequest request) {
        return scheduleService.create(request);
    }

    @PutMapping("/maintenance/schedules/{id}")
    @PreAuthorize("hasAuthority('MAINTENANCE_MANAGE')")
    public ScheduleResponse update(@PathVariable Long id, @Valid @RequestBody ScheduleRequest request) {
        return scheduleService.update(id, request);
    }

    @DeleteMapping("/maintenance/schedules/{id}")
    @PreAuthorize("hasAuthority('MAINTENANCE_MANAGE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Deactivate a schedule")
    public void deactivate(@PathVariable Long id) {
        scheduleService.deactivate(id);
    }

    @PostMapping("/maintenance/schedules/refresh")
    @PreAuthorize("hasAuthority('MAINTENANCE_MANAGE')")
    @Operation(summary = "Recompute every active schedule and every vehicle's maintenance status now (same as the nightly job)")
    public ScheduleRefreshResponse refresh() {
        return scheduleService.refreshAll();
    }
}
