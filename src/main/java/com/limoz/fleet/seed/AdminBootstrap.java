package com.limoz.fleet.seed;

import com.limoz.fleet.config.AppProperties;
import com.limoz.fleet.security.Roles;
import com.limoz.fleet.user.RoleRepository;
import com.limoz.fleet.user.User;
import com.limoz.fleet.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Creates the first SUPER_ADMIN account when the user table is empty, from ADMIN_EMAIL / ADMIN_PASSWORD.
 * Runs in every environment so a fresh installation is usable without editing source code.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }
        AppProperties.Bootstrap bootstrap = properties.bootstrap();
        if (bootstrap.adminPassword() == null || bootstrap.adminPassword().isBlank()) {
            log.warn("No users exist and ADMIN_PASSWORD is not set - skipping administrator bootstrap. "
                    + "Set ADMIN_EMAIL and ADMIN_PASSWORD and restart.");
            return;
        }
        User admin = new User();
        admin.setFirstName(bootstrap.adminFirstName());
        admin.setLastName(bootstrap.adminLastName());
        admin.setEmail(bootstrap.adminEmail().toLowerCase());
        admin.setPasswordHash(passwordEncoder.encode(bootstrap.adminPassword()));
        admin.setActive(true);
        admin.setRoles(Set.of(roleRepository.findByCode(Roles.SUPER_ADMIN).orElseThrow()));
        userRepository.save(admin);
        log.info("Bootstrap SUPER_ADMIN account created: {}", admin.getEmail());
    }
}
