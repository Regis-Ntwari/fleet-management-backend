package com.limoz.fleet.incident.controller;

import com.limoz.fleet.incident.service.TrafficFineService;

import com.limoz.fleet.common.api.PageResponse;
import com.limoz.fleet.finance.dto.SettlementRequest;
import com.limoz.fleet.incident.dto.FineNoteRequest;
import com.limoz.fleet.incident.dto.TrafficFineFilter;
import com.limoz.fleet.incident.dto.TrafficFineRequest;
import com.limoz.fleet.incident.dto.TrafficFineResponse;
import com.limoz.fleet.incident.dto.TrafficFineSummaryResponse;
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

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Traffic Fines", description = "Penalties issued against fleet vehicles and drivers, settled through the payment ledger")
public class TrafficFineController {

    private final TrafficFineService trafficFineService;

    @GetMapping("/traffic-fines")
    @PreAuthorize("hasAuthority('INCIDENT_READ')")
    @Operation(summary = "Search fines (paginated)", description = "Example: `?q=parking&status=UNPAID&status=DISPUTED&vehicleId=4`")
    public PageResponse<TrafficFineResponse> search(@ParameterObject TrafficFineFilter filter,
                                                    @ParameterObject @PageableDefault(size = 20, sort = "issuedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return trafficFineService.search(filter, pageable);
    }

    @GetMapping("/traffic-fines/summary")
    @PreAuthorize("hasAuthority('INCIDENT_READ')")
    @Operation(summary = "Fine KPIs: total, unpaid, disputed, outstanding amount (default last 12 months)")
    public TrafficFineSummaryResponse summary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return trafficFineService.summary(from, to);
    }

    @GetMapping("/traffic-fines/{id}")
    @PreAuthorize("hasAuthority('INCIDENT_READ')")
    public TrafficFineResponse get(@PathVariable Long id) {
        return trafficFineService.get(id);
    }

    @PostMapping("/traffic-fines")
    @PreAuthorize("hasAuthority('FINE_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Log a fine (driver defaults to the vehicle's current driver)")
    public TrafficFineResponse record(@Valid @RequestBody TrafficFineRequest request) {
        return trafficFineService.record(request);
    }

    @PutMapping("/traffic-fines/{id}")
    @PreAuthorize("hasAuthority('FINE_MANAGE')")
    public TrafficFineResponse update(@PathVariable Long id, @Valid @RequestBody TrafficFineRequest request) {
        return trafficFineService.update(id, request);
    }

    @PostMapping("/traffic-fines/{id}/pay")
    @PreAuthorize("hasAuthority('FINE_MANAGE')")
    @Operation(summary = "Pay a fine (records an OUT payment in the ledger)")
    public TrafficFineResponse pay(@PathVariable Long id, @Valid @RequestBody SettlementRequest request) {
        return trafficFineService.pay(id, request);
    }

    @PostMapping("/traffic-fines/{id}/dispute")
    @PreAuthorize("hasAuthority('FINE_MANAGE')")
    @Operation(summary = "UNPAID -> DISPUTED")
    public TrafficFineResponse dispute(@PathVariable Long id, @Valid @RequestBody FineNoteRequest request) {
        return trafficFineService.dispute(id, request.note());
    }

    @PostMapping("/traffic-fines/{id}/waive")
    @PreAuthorize("hasAuthority('FINE_MANAGE')")
    @Operation(summary = "UNPAID / DISPUTED -> WAIVED")
    public TrafficFineResponse waive(@PathVariable Long id, @Valid @RequestBody FineNoteRequest request) {
        return trafficFineService.waive(id, request.note());
    }

    @GetMapping("/vehicles/{vehicleId}/fines")
    @PreAuthorize("hasAuthority('INCIDENT_READ')")
    @Operation(summary = "Fines of a vehicle")
    public PageResponse<TrafficFineResponse> forVehicle(@PathVariable Long vehicleId,
                                                        @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return trafficFineService.forVehicle(vehicleId, pageable);
    }

    @GetMapping("/drivers/{driverId}/fines")
    @PreAuthorize("hasAuthority('INCIDENT_READ')")
    @Operation(summary = "Fines of a driver")
    public PageResponse<TrafficFineResponse> forDriver(@PathVariable Long driverId,
                                                       @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return trafficFineService.forDriver(driverId, pageable);
    }
}
