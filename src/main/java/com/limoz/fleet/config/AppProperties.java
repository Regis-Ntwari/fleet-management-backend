package com.limoz.fleet.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * Strongly typed application configuration bound from the {@code fleet.*} namespace.
 * Operational thresholds that operations staff may change live in {@link com.limoz.fleet.settings.SystemSetting}
 * instead; this class only holds deployment-level configuration.
 */
@Validated
@ConfigurationProperties(prefix = "fleet")
public record AppProperties(
        @NotBlank String timezone,
        @NotBlank String currency,
        Cors cors,
        Security security,
        Storage storage,
        Bootstrap bootstrap,
        Seed seed,
        Telematics telematics,
        Jobs jobs) {

    public record Cors(List<String> allowedOrigins) {}

    public record Security(Jwt jwt, Login login) {}

    public record Jwt(String secret, Duration accessTokenTtl, Duration refreshTokenTtl, String issuer) {}

    public record Login(int maxAttemptsPerWindow, Duration window) {}

    public record Storage(String provider, Local local) {
        public record Local(String basePath) {}
    }

    public record Bootstrap(String adminEmail, String adminPassword, String adminFirstName, String adminLastName) {}

    public record Seed(boolean enabled) {}

    public record Telematics(String provider, String syncCron) {}

    public record Jobs(String documentStatusCron, String maintenanceDueCron, String alertScanCron,
                       String dailyMovementCron, String tokenCleanupCron) {}
}
