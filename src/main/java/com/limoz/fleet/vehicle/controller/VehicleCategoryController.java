package com.limoz.fleet.vehicle.controller;

import com.limoz.fleet.vehicle.domain.Vehicle;
import com.limoz.fleet.vehicle.service.VehicleCategoryService;

import com.limoz.fleet.vehicle.dto.VehicleCategoryRequest;
import com.limoz.fleet.vehicle.dto.VehicleCategoryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/vehicle-categories")
@RequiredArgsConstructor
@Tag(name = "Vehicle Categories", description = "Configurable fleet categories")
public class VehicleCategoryController {

    private final VehicleCategoryService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List categories")
    public List<VehicleCategoryResponse> list() {
        return service.list();
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('SETTINGS_MANAGE','VEHICLE_CREATE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a category")
    public VehicleCategoryResponse create(@Valid @RequestBody VehicleCategoryRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SETTINGS_MANAGE','VEHICLE_UPDATE')")
    @Operation(summary = "Update a category")
    public VehicleCategoryResponse update(@PathVariable Long id, @Valid @RequestBody VehicleCategoryRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an unused category")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
