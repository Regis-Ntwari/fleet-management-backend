package com.limoz.fleet.fuel;

import com.limoz.fleet.common.api.PageResponse;
import com.limoz.fleet.fuel.dto.FleetFuelSummaryResponse;
import com.limoz.fleet.fuel.dto.FuelFilter;
import com.limoz.fleet.fuel.dto.FuelSummaryResponse;
import com.limoz.fleet.fuel.dto.FuelTransactionRequest;
import com.limoz.fleet.fuel.dto.FuelTransactionResponse;
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
@Tag(name = "Fuel", description = "Fuel log with consumption, sensor variance and anomaly detection")
public class FuelController {

    private final FuelService fuelService;

    @GetMapping("/fuel")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    @Operation(summary = "Search fuel transactions",
            description = "Example: `?q=SP Nyabugogo&vehicleId=3&from=2026-06-01&to=2026-06-30&anomaly=true&page=0&size=20`")
    public PageResponse<FuelTransactionResponse> search(@ParameterObject FuelFilter filter,
                                                        @ParameterObject @PageableDefault(size = 20, sort = "transactionAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return fuelService.search(filter, pageable);
    }

    @GetMapping("/fuel/summary")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    @Operation(summary = "Fleet fuel totals and per-vehicle breakdown (default: last 30 days)")
    public FleetFuelSummaryResponse fleetSummary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                 @RequestParam(required = false) Long vehicleId,
                                                 @RequestParam(required = false) Long categoryId) {
        return fuelService.fleetSummary(from, to, vehicleId, categoryId);
    }

    @GetMapping("/fuel/{id}")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public FuelTransactionResponse get(@PathVariable Long id) {
        return fuelService.get(id);
    }

    @PostMapping("/fuel")
    @PreAuthorize("hasAuthority('FUEL_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a refuelling (total, distance, consumption and anomaly flag are computed)")
    public FuelTransactionResponse create(@Valid @RequestBody FuelTransactionRequest request) {
        return fuelService.create(request);
    }

    @PutMapping("/fuel/{id}")
    @PreAuthorize("hasAuthority('FUEL_MANAGE')")
    @Operation(summary = "Edit a refuelling (all derived figures are recomputed, including the next transaction's distance)")
    public FuelTransactionResponse update(@PathVariable Long id, @Valid @RequestBody FuelTransactionRequest request) {
        return fuelService.update(id, request);
    }

    @DeleteMapping("/fuel/{id}")
    @PreAuthorize("hasAuthority('FUEL_MANAGE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Archive a refuelling (soft delete)")
    public void archive(@PathVariable Long id) {
        fuelService.archive(id);
    }

    @GetMapping("/vehicles/{vehicleId}/fuel")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    @Operation(summary = "Fuel history of a vehicle")
    public PageResponse<FuelTransactionResponse> forVehicle(@PathVariable Long vehicleId,
                                                            @ParameterObject @PageableDefault(size = 20, sort = "transactionAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return fuelService.forVehicle(vehicleId, pageable);
    }

    @GetMapping("/vehicles/{vehicleId}/fuel/summary")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    @Operation(summary = "Fuel totals of a vehicle: litres, cost, distance, average L/100km, km/L, anomalies (default: last 30 days)")
    public FuelSummaryResponse vehicleSummary(@PathVariable Long vehicleId,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return fuelService.vehicleSummary(vehicleId, from, to);
    }
}
