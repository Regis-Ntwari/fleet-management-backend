package com.limoz.fleet.security;

import java.util.Set;

/**
 * Lightweight principal placed in the security context after JWT validation.
 */
public record AuthenticatedUser(Long id, String email, String fullName, Set<String> roles, Set<String> permissions, Long driverId) {

    public boolean hasRole(String roleCode) {
        return roles.contains(roleCode);
    }

    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }
}
