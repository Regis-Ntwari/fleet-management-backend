package com.limoz.fleet.user.dto;

import java.time.Instant;
import java.util.Set;

public record UserResponse(
        Long id,
        String firstName,
        String lastName,
        String fullName,
        String email,
        String phone,
        String username,
        boolean active,
        boolean mustChangePassword,
        Instant lastLoginAt,
        Long driverId,
        Set<String> roles,
        Set<String> permissions,
        Instant createdAt,
        Instant updatedAt) {}
