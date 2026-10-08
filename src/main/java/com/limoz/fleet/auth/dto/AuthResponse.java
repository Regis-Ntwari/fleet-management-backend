package com.limoz.fleet.auth.dto;

import com.limoz.fleet.user.dto.UserResponse;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserResponse user) {}
