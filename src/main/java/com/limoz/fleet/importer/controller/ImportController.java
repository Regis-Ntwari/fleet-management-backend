package com.limoz.fleet.importer.controller;

import com.limoz.fleet.importer.service.DriverImporter;
import com.limoz.fleet.importer.service.VehicleImporter;

import com.limoz.fleet.audit.domain.AuditAction;
import com.limoz.fleet.audit.service.AuditService;
import com.limoz.fleet.importer.dto.ImportResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/imports")
@RequiredArgsConstructor
@Tag(name = "Imports", description = "CSV / Excel imports with per-row validation and duplicate detection")
@PreAuthorize("hasAuthority('IMPORT_DATA')")
public class ImportController {

    private final VehicleImporter vehicleImporter;
    private final DriverImporter driverImporter;
    private final AuditService auditService;

    @GetMapping("/templates")
    @Operation(summary = "Expected column names per import type")
    public Map<String, List<String>> templates() {
        return Map.of("vehicles", VehicleImporter.COLUMNS, "drivers", DriverImporter.COLUMNS);
    }

    @PostMapping(value = "/vehicles", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import a vehicle list (CSV or Excel)")
    public ImportResult vehicles(@RequestPart("file") MultipartFile file,
                                 @RequestParam(defaultValue = "false") boolean updateExisting) {
        ImportResult result = vehicleImporter.importFile(file, updateExisting);
        auditService.record(AuditAction.IMPORT, "Vehicle", null, file.getOriginalFilename(), null, result,
                "Vehicle import: " + result.imported() + " imported, " + result.updated() + " updated, " + result.rejected() + " rejected");
        return result;
    }

    @PostMapping(value = "/drivers", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import a driver list (CSV or Excel)")
    public ImportResult drivers(@RequestPart("file") MultipartFile file,
                                @RequestParam(defaultValue = "false") boolean updateExisting) {
        ImportResult result = driverImporter.importFile(file, updateExisting);
        auditService.record(AuditAction.IMPORT, "Driver", null, file.getOriginalFilename(), null, result,
                "Driver import: " + result.imported() + " imported, " + result.updated() + " updated, " + result.rejected() + " rejected");
        return result;
    }
}
