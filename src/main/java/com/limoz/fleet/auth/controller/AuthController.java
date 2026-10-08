package com.limoz.fleet.auth.controller;

import com.limoz.fleet.auth.service.AuthService;

import com.limoz.fleet.auth.dto.AuthResponse;
import com.limoz.fleet.auth.dto.LoginRequest;
import com.limoz.fleet.auth.dto.LogoutRequest;
import com.limoz.fleet.auth.dto.RefreshRequest;
import com.limoz.fleet.security.FleetAuthenticationToken;
import com.limoz.fleet.security.SecurityUtils;
import com.limoz.fleet.user.mapper.UserMapper;
import com.limoz.fleet.user.service.UserService;
import com.limoz.fleet.user.dto.ChangePasswordRequest;
import com.limoz.fleet.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final UserMapper userMapper;

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Authenticate with email and password", description = "Returns a short-lived JWT access token and a rotating refresh token.")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    @Operation(summary = "Exchange a refresh token for a new token pair")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke the current access token and the given (or all) refresh tokens")
    public void logout(Authentication authentication, @RequestBody(required = false) LogoutRequest request) {
        var jwt = authentication instanceof FleetAuthenticationToken token ? token.getJwt() : null;
        authService.logout(jwt, request == null ? null : request.refreshToken());
    }

    @GetMapping("/me")
    @Operation(summary = "Current user profile, roles and permissions")
    public UserResponse me() {
        return userMapper.toResponse(userService.load(SecurityUtils.requireCurrentUser().id()));
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Change own password (invalidates existing sessions)")
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        Long userId = SecurityUtils.requireCurrentUser().id();
        userService.changeOwnPassword(userId, request);
        authService.revokeAllSessions(userId);
    }
}
