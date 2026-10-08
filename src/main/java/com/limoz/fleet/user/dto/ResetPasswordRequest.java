package com.limoz.fleet.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank @Size(min = 8, max = 100) String newPassword,
        boolean mustChangePassword) {}
