package com.limoz.fleet.incident.controller;

import com.limoz.fleet.incident.domain.Incident;
import com.limoz.fleet.incident.service.IncidentService;

import com.limoz.fleet.common.api.PageResponse;
import com.limoz.fleet.incident.dto.IncidentFilter;
import com.limoz.fleet.incident.dto.IncidentNoteRequest;
import com.limoz.fleet.incident.dto.IncidentRequest;
import com.limoz.fleet.incident.dto.IncidentResolveRequest;
import com.limoz.fleet.incident.dto.IncidentResponse;
import com.limoz.fleet.incident.dto.IncidentSummaryResponse;
import com.limoz.fleet.incident.dto.IncidentUpdateResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Incidents & Accidents", description = "Incident reports, investigation history and resolution")
public class IncidentController {

    private final IncidentService incidentService;

    @GetMapping("/incidents")
    @PreAuthorize("hasAuthority('INCIDENT_READ')")
    @Operation(summary = "Search incidents (paginated)",
            description = "Example: `?q=RAD&incidentType=ACCIDENT&severity=MAJOR&status=OPEN&status=UNDER_INVESTIGATION&from=2026-01-01`")
    public PageResponse<IncidentResponse> search(@ParameterObject IncidentFilter filter,
                                                 @ParameterObject @PageableDefault(size = 20, sort = "occurredAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return incidentService.search(filter, pageable);
    }

    @GetMapping("/incidents/summary")
    @PreAuthorize("hasAuthority('INCIDENT_READ')")
    @Operation(summary = "Counts by type / severity / status and top vehicles and drivers (default last 12 months)")
    public IncidentSummaryResponse summary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return incidentService.summary(from, to);
    }

    @GetMapping("/incidents/{id}")
    @PreAuthorize("hasAuthority('INCIDENT_READ')")
    @Operation(summary = "Incident detail with its full update history")
    public IncidentResponse get(@PathVariable Long id) {
        return incidentService.get(id);
    }

    @GetMapping("/incidents/{id}/updates")
    @PreAuthorize("hasAuthority('INCIDENT_READ')")
    public List<IncidentUpdateResponse> history(@PathVariable Long id) {
        return incidentService.history(id);
    }

    @PostMapping("/incidents")
    @PreAuthorize("hasAuthority('INCIDENT_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Report an incident (a MAJOR / CRITICAL accident takes the vehicle out of service)")
    public IncidentResponse report(@Valid @RequestBody IncidentRequest request) {
        return incidentService.report(request);
    }

    @PutMapping("/incidents/{id}")
    @PreAuthorize("hasAuthority('INCIDENT_MANAGE')")
    public IncidentResponse update(@PathVariable Long id, @Valid @RequestBody IncidentRequest request) {
        return incidentService.update(id, request);
    }

    @PostMapping("/incidents/{id}/updates")
    @PreAuthorize("hasAuthority('INCIDENT_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Append an investigation note")
    public IncidentUpdateResponse addNote(@PathVariable Long id, @Valid @RequestBody IncidentNoteRequest request) {
        return incidentService.addNote(id, request.note());
    }

    @PostMapping("/incidents/{id}/investigate")
    @PreAuthorize("hasAuthority('INCIDENT_MANAGE')")
    @Operation(summary = "OPEN -> UNDER_INVESTIGATION")
    public IncidentResponse investigate(@PathVariable Long id, @RequestBody(required = false) IncidentNoteRequest request) {
        return incidentService.startInvestigation(id, request == null ? null : request.note());
    }

    @PostMapping("/incidents/{id}/resolve")
    @PreAuthorize("hasAuthority('INCIDENT_MANAGE')")
    @Operation(summary = "-> RESOLVED with the corrective action taken")
    public IncidentResponse resolve(@PathVariable Long id, @Valid @RequestBody IncidentResolveRequest request) {
        return incidentService.resolve(id, request.correctiveAction(), request.note());
    }

    @PostMapping("/incidents/{id}/close")
    @PreAuthorize("hasAuthority('INCIDENT_MANAGE')")
    @Operation(summary = "RESOLVED -> CLOSED")
    public IncidentResponse close(@PathVariable Long id, @RequestBody(required = false) IncidentNoteRequest request) {
        return incidentService.close(id, request == null ? null : request.note());
    }

    @PostMapping("/incidents/{id}/reopen")
    @PreAuthorize("hasAuthority('INCIDENT_MANAGE')")
    @Operation(summary = "RESOLVED / CLOSED -> UNDER_INVESTIGATION (reason required)")
    public IncidentResponse reopen(@PathVariable Long id, @Valid @RequestBody IncidentNoteRequest request) {
        return incidentService.reopen(id, request.note());
    }

    @GetMapping("/vehicles/{vehicleId}/incidents")
    @PreAuthorize("hasAuthority('INCIDENT_READ')")
    @Operation(summary = "Incident history of a vehicle")
    public PageResponse<IncidentResponse> forVehicle(@PathVariable Long vehicleId,
                                                     @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return incidentService.forVehicle(vehicleId, pageable);
    }

    @GetMapping("/drivers/{driverId}/incidents")
    @PreAuthorize("hasAuthority('INCIDENT_READ')")
    @Operation(summary = "Incident history of a driver")
    public PageResponse<IncidentResponse> forDriver(@PathVariable Long driverId,
                                                    @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return incidentService.forDriver(driverId, pageable);
    }
}
