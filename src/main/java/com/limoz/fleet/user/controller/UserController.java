package com.limoz.fleet.user.controller;

import com.limoz.fleet.user.domain.User;
import com.limoz.fleet.user.service.UserService;

import com.limoz.fleet.common.api.PageResponse;
import com.limoz.fleet.user.dto.CreateUserRequest;
import com.limoz.fleet.user.dto.ResetPasswordRequest;
import com.limoz.fleet.user.dto.UpdateUserRequest;
import com.limoz.fleet.user.dto.UserResponse;
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

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User account administration")
@PreAuthorize("hasAuthority('USER_MANAGE')")
public class UserController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "Search users (paginated)")
    public PageResponse<UserResponse> search(@RequestParam(required = false) String q,
                                             @RequestParam(required = false) String role,
                                             @RequestParam(required = false) Boolean active,
                                             @ParameterObject @PageableDefault(size = 20, sort = "lastName", direction = Sort.Direction.ASC) Pageable pageable) {
        return userService.search(q, role, active, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a user")
    public UserResponse get(@PathVariable Long id) {
        return userService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a user account")
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return userService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a user's profile and roles")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(id, request);
    }

    @PostMapping("/{id}/enable")
    @Operation(summary = "Enable a user")
    public UserResponse enable(@PathVariable Long id) {
        return userService.setActive(id, true);
    }

    @PostMapping("/{id}/disable")
    @Operation(summary = "Disable a user (soft - history is retained)")
    public UserResponse disable(@PathVariable Long id) {
        return userService.setActive(id, false);
    }

    @PostMapping("/{id}/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Administrator password reset")
    public void resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(id, request);
    }
}
