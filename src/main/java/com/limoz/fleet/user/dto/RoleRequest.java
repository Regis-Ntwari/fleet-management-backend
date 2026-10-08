package com.limoz.fleet.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record RoleRequest(
        @NotBlank @Size(max = 50) @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "code must be UPPER_SNAKE_CASE") String code,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 255) String description,
        Set<String> permissions) {}
