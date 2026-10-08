package com.limoz.fleet.seed.service;

import com.limoz.fleet.security.AuthenticatedUser;
import com.limoz.fleet.security.FleetAuthenticationToken;
import com.limoz.fleet.user.domain.User;
import com.limoz.fleet.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Runs seed steps as the bootstrap SUPER_ADMIN so that services which record the acting user
 * (incident reporter, expense submitter, audit trail) behave exactly as in interactive use.
 */
@Component
@RequiredArgsConstructor
public class SeedSecurity {

    private final UserRepository userRepository;

    public void runAsAdministrator(Runnable action) {
        User admin = userRepository.findActiveByRoleCodes(Set.of("SUPER_ADMIN")).stream().findFirst()
                .flatMap(u -> userRepository.findWithRolesById(u.getId()))
                .orElseThrow(() -> new IllegalStateException("No SUPER_ADMIN user exists - bootstrap must run before seeding"));
        AuthenticatedUser principal = new AuthenticatedUser(admin.getId(), admin.getEmail(), admin.getFullName(),
                Set.copyOf(admin.roleCodes()), Set.copyOf(admin.permissionCodes()), admin.getDriverId());
        var previous = SecurityContextHolder.getContext().getAuthentication();
        SecurityContextHolder.getContext().setAuthentication(new FleetAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))));
        try {
            action.run();
        } finally {
            SecurityContextHolder.getContext().setAuthentication(previous);
        }
    }
}
