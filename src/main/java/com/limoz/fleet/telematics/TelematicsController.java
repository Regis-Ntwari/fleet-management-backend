package com.limoz.fleet.telematics;

import com.limoz.fleet.common.exception.BusinessRuleException;
import com.limoz.fleet.telematics.dto.DeviceFilter;
import com.limoz.fleet.telematics.dto.DeviceProblemsResponse;
import com.limoz.fleet.telematics.dto.DeviceRequest;
import com.limoz.fleet.telematics.dto.DeviceResponse;
import com.limoz.fleet.telematics.dto.FuelSensorStatusRequest;
import com.limoz.fleet.telematics.dto.GpsRefreshResponse;
import com.limoz.fleet.telematics.dto.IngestResult;
import com.limoz.fleet.telematics.dto.LatestPositionResponse;
import com.limoz.fleet.telematics.dto.PositionInput;
import com.limoz.fleet.telematics.dto.PositionSeriesResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/telematics")
@RequiredArgsConstructor
@Tag(name = "Telematics", description = "GPS devices, position ingestion and live positions")
public class TelematicsController {

    public static final String SOURCE_API = "api";
    public static final String SOURCE_CSV = "csv";

    private final TelematicsDeviceService deviceService;
    private final PositionIngestService ingestService;
    private final TelematicsQueryService queryService;

    // ---------------------------------------------------------------- devices

    @GetMapping("/devices")
    @PreAuthorize("hasAuthority('TELEMATICS_READ')")
    @Operation(summary = "Devices of non-archived vehicles with health (filter by gpsStatus / fuelSensorStatus / active)")
    public List<DeviceResponse> devices(@ParameterObject DeviceFilter filter) {
        return deviceService.list(filter);
    }

    @GetMapping("/devices/problems")
    @PreAuthorize("hasAuthority('TELEMATICS_READ')")
    @Operation(summary = "Devices with GPS problems (OFFLINE / NO_SIGNAL / DISCONNECTED) or a faulty fuel sensor")
    public DeviceProblemsResponse problems() {
        return deviceService.problems();
    }

    @GetMapping("/devices/{id}")
    @PreAuthorize("hasAuthority('TELEMATICS_READ')")
    public DeviceResponse device(@PathVariable Long id) {
        return deviceService.get(id);
    }

    @GetMapping("/vehicles/{vehicleId}/device")
    @PreAuthorize("hasAuthority('TELEMATICS_READ')")
    @Operation(summary = "Device registered on a vehicle")
    public DeviceResponse deviceOfVehicle(@PathVariable Long vehicleId) {
        return deviceService.forVehicle(vehicleId);
    }

    @PostMapping("/devices")
    @PreAuthorize("hasAuthority('TELEMATICS_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a device on a vehicle (one device per vehicle)")
    public DeviceResponse register(@Valid @RequestBody DeviceRequest request) {
        return deviceService.register(request);
    }

    @PutMapping("/devices/{id}")
    @PreAuthorize("hasAuthority('TELEMATICS_MANAGE')")
    public DeviceResponse update(@PathVariable Long id, @Valid @RequestBody DeviceRequest request) {
        return deviceService.update(id, request);
    }

    @PostMapping("/devices/{id}/deactivate")
    @PreAuthorize("hasAuthority('TELEMATICS_MANAGE')")
    @Operation(summary = "Deactivate a device (status becomes DISCONNECTED)")
    public DeviceResponse deactivate(@PathVariable Long id, @RequestParam(required = false) String reason) {
        return deviceService.deactivate(id, reason);
    }

    @PostMapping("/devices/{id}/activate")
    @PreAuthorize("hasAuthority('TELEMATICS_MANAGE')")
    public DeviceResponse activate(@PathVariable Long id) {
        return deviceService.activate(id);
    }

    @PatchMapping("/devices/{id}/fuel-sensor")
    @PreAuthorize("hasAuthority('TELEMATICS_MANAGE')")
    @Operation(summary = "Set the fuel sensor status (OK / FAULTY / NOT_INSTALLED / UNKNOWN)")
    public DeviceResponse fuelSensor(@PathVariable Long id, @Valid @RequestBody FuelSensorStatusRequest request) {
        return deviceService.setFuelSensorStatus(id, request);
    }

    @PostMapping("/devices/refresh-status")
    @PreAuthorize("hasAuthority('TELEMATICS_MANAGE')")
    @Operation(summary = "Recompute ONLINE / OFFLINE / NO_SIGNAL / DISCONNECTED for all devices now (also runs with the sync job)")
    public GpsRefreshResponse refreshStatuses() {
        return deviceService.refreshGpsStatuses();
    }

    // ---------------------------------------------------------------- positions

    @PostMapping("/positions")
    @PreAuthorize("hasAuthority('TELEMATICS_MANAGE')")
    @Operation(summary = "Ingest a JSON batch of positions (by vehicleId, plateNumber or externalDeviceId); max 5000 rows",
            description = "Invalid rows are reported per row in the result and never imported; duplicates (same vehicle and timestamp) are skipped.")
    public IngestResult ingest(@RequestBody @NotEmpty @Size(max = PositionIngestService.MAX_BATCH) List<PositionInput> rows) {
        return ingestService.ingest(rows, SOURCE_API);
    }

    @PostMapping(value = "/positions/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('TELEMATICS_MANAGE')")
    @Operation(summary = "Import positions from CSV (columns plate,recordedAt,latitude,longitude,speedKph,odometerKm,ignition)")
    public IngestResult importCsv(@RequestPart("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessRuleException("EMPTY_FILE", "The uploaded file is empty");
        }
        try (InputStream in = file.getInputStream()) {
            return ingestService.importCsv(in, SOURCE_CSV);
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not read the uploaded file", ex);
        }
    }

    @GetMapping("/vehicles/{vehicleId}/positions")
    @PreAuthorize("hasAuthority('TELEMATICS_READ')")
    @Operation(summary = "Position history of a vehicle (default last 24h, max 31 days, capped at 5000 rows)")
    public PositionSeriesResponse positions(@PathVariable Long vehicleId,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return queryService.positions(vehicleId, from, to);
    }

    @GetMapping("/latest")
    @PreAuthorize("hasAuthority('TELEMATICS_READ')")
    @Operation(summary = "Latest known position of every vehicle (live map)")
    public List<LatestPositionResponse> latest() {
        return queryService.latest();
    }
}
