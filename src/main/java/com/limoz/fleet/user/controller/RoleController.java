package com.limoz.fleet.user.controller;

import com.limoz.fleet.user.service.RoleService;

import com.limoz.fleet.user.dto.PermissionResponse;
import com.limoz.fleet.user.dto.RoleRequest;
import com.limoz.fleet.user.dto.RoleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@Tag(name = "Roles & Permissions")
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','ROLE_MANAGE')")
    @Operation(summary = "List roles with their permissions")
    public List<RoleResponse> list() {
        return roleService.listRoles();
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','ROLE_MANAGE')")
    @Operation(summary = "List all permission codes")
    public List<PermissionResponse> permissions() {
        return roleService.listPermissions();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a custom role")
    public RoleResponse create(@Valid @RequestBody RoleRequest request) {
        return roleService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    @Operation(summary = "Update a role's permissions")
    public RoleResponse update(@PathVariable Long id, @Valid @RequestBody RoleRequest request) {
        return roleService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a custom role")
    public void delete(@PathVariable Long id) {
        roleService.delete(id);
    }
}
