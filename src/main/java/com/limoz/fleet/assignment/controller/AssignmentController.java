package com.limoz.fleet.assignment.controller;

import com.limoz.fleet.assignment.service.AssignmentService;

import com.limoz.fleet.assignment.dto.AssignmentRequest;
import com.limoz.fleet.assignment.dto.AssignmentResponse;
import com.limoz.fleet.assignment.dto.EndAssignmentRequest;
import com.limoz.fleet.common.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/assignments")
@RequiredArgsConstructor
@Tag(name = "Vehicle Assignments", description = "Vehicle -> driver responsibility, with full history")
public class AssignmentController {

    private final AssignmentService assignmentService;

    @GetMapping("/active")
    @PreAuthorize("hasAuthority('ASSIGNMENT_READ')")
    @Operation(summary = "All active assignments")
    public List<AssignmentResponse> active() {
        return assignmentService.active();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ASSIGNMENT_READ')")
    public AssignmentResponse get(@PathVariable Long id) {
        return assignmentService.get(id);
    }

    @GetMapping("/vehicle/{vehicleId}")
    @PreAuthorize("hasAuthority('ASSIGNMENT_READ')")
    @Operation(summary = "Assignment history of a vehicle")
    public PageResponse<AssignmentResponse> forVehicle(@PathVariable Long vehicleId, @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return assignmentService.historyForVehicle(vehicleId, pageable);
    }

    @GetMapping("/driver/{driverId}")
    @PreAuthorize("hasAuthority('ASSIGNMENT_READ')")
    @Operation(summary = "Assignment history of a driver")
    public PageResponse<AssignmentResponse> forDriver(@PathVariable Long driverId, @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return assignmentService.historyForDriver(driverId, pageable);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ASSIGNMENT_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Assign a vehicle to a driver", description = "Validates vehicle availability, driver availability, licence validity and conflicting active assignments.")
    public AssignmentResponse assign(@Valid @RequestBody AssignmentRequest request) {
        return assignmentService.assign(request);
    }

    @PostMapping("/{id}/end")
    @PreAuthorize("hasAuthority('ASSIGNMENT_MANAGE')")
    @Operation(summary = "End (or cancel) an active assignment and record the return odometer")
    public AssignmentResponse end(@PathVariable Long id, @Valid @RequestBody(required = false) EndAssignmentRequest request) {
        return assignmentService.end(id, request == null ? new EndAssignmentRequest(null, null, null, false) : request);
    }
}
