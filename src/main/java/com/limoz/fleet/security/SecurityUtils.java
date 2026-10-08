package com.limoz.fleet.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static Optional<AuthenticatedUser> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public static Optional<Long> currentUserId() {
        return currentUser().map(AuthenticatedUser::id);
    }

    public static Optional<String> currentUsername() {
        return currentUser().map(AuthenticatedUser::email);
    }

    public static AuthenticatedUser requireCurrentUser() {
        return currentUser().orElseThrow(() -> new IllegalStateException("No authenticated user in context"));
    }

    public static boolean hasPermission(String permission) {
        return currentUser().map(u -> u.hasPermission(permission)).orElse(false);
    }
}
