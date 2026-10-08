package com.limoz.fleet.maintenance;

import com.limoz.fleet.maintenance.dto.ServiceTypeRequest;
import com.limoz.fleet.maintenance.dto.ServiceTypeResponse;
import com.limoz.fleet.maintenance.dto.WorkshopRequest;
import com.limoz.fleet.maintenance.dto.WorkshopResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Workshops & Service Types", description = "Maintenance reference data: garages / vendors and the service catalogue")
public class ReferenceDataController {

    private final WorkshopService workshopService;
    private final ServiceTypeService serviceTypeService;

    // ---------------------------------------------------------------- workshops

    @GetMapping("/workshops")
    @PreAuthorize("hasAuthority('MAINTENANCE_READ')")
    @Operation(summary = "List workshops (internal garage and external vendors)")
    public List<WorkshopResponse> workshops() {
        return workshopService.list();
    }

    @GetMapping("/workshops/{id}")
    @PreAuthorize("hasAuthority('MAINTENANCE_READ')")
    public WorkshopResponse workshop(@PathVariable Long id) {
        return workshopService.get(id);
    }

    @PostMapping("/workshops")
    @PreAuthorize("hasAnyAuthority('MAINTENANCE_MANAGE','SETTINGS_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkshopResponse createWorkshop(@Valid @RequestBody WorkshopRequest request) {
        return workshopService.create(request);
    }

    @PutMapping("/workshops/{id}")
    @PreAuthorize("hasAnyAuthority('MAINTENANCE_MANAGE','SETTINGS_MANAGE')")
    public WorkshopResponse updateWorkshop(@PathVariable Long id, @Valid @RequestBody WorkshopRequest request) {
        return workshopService.update(id, request);
    }

    @DeleteMapping("/workshops/{id}")
    @PreAuthorize("hasAnyAuthority('MAINTENANCE_MANAGE','SETTINGS_MANAGE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a workshop (deactivated instead when jobs reference it)")
    public void deleteWorkshop(@PathVariable Long id) {
        workshopService.delete(id);
    }

    // ---------------------------------------------------------------- service types

    @GetMapping("/service-types")
    @PreAuthorize("hasAuthority('MAINTENANCE_READ')")
    @Operation(summary = "Service / repair / inspection catalogue with default preventive intervals")
    public List<ServiceTypeResponse> serviceTypes() {
        return serviceTypeService.list();
    }

    @GetMapping("/service-types/{id}")
    @PreAuthorize("hasAuthority('MAINTENANCE_READ')")
    public ServiceTypeResponse serviceType(@PathVariable Long id) {
        return serviceTypeService.get(id);
    }

    @PostMapping("/service-types")
    @PreAuthorize("hasAnyAuthority('MAINTENANCE_MANAGE','SETTINGS_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceTypeResponse createServiceType(@Valid @RequestBody ServiceTypeRequest request) {
        return serviceTypeService.create(request);
    }

    @PutMapping("/service-types/{id}")
    @PreAuthorize("hasAnyAuthority('MAINTENANCE_MANAGE','SETTINGS_MANAGE')")
    public ServiceTypeResponse updateServiceType(@PathVariable Long id, @Valid @RequestBody ServiceTypeRequest request) {
        return serviceTypeService.update(id, request);
    }

    @DeleteMapping("/service-types/{id}")
    @PreAuthorize("hasAnyAuthority('MAINTENANCE_MANAGE','SETTINGS_MANAGE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a service type (deactivated instead when tasks or schedules use it)")
    public void deleteServiceType(@PathVariable Long id) {
        serviceTypeService.delete(id);
    }
}
