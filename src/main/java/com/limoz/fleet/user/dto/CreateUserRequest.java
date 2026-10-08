package com.limoz.fleet.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateUserRequest(
        @NotBlank @Size(max = 80) String firstName,
        @NotBlank @Size(max = 80) String lastName,
        @NotBlank @Email @Size(max = 150) String email,
        @Size(max = 30) String phone,
        @Size(min = 3, max = 60) @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "username may contain letters, digits, dot, underscore and dash") String username,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotEmpty Set<String> roles,
        Long driverId,
        boolean mustChangePassword) {}
