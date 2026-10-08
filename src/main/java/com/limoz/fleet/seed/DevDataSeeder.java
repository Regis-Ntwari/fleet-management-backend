package com.limoz.fleet.seed;

import com.limoz.fleet.config.AppProperties;
import com.limoz.fleet.vehicle.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

/**
 * Loads a realistic LIMOZ-style dataset on an empty database when {@code fleet.seed.enabled=true}
 * (dev profile default). Each module contributes a {@link SeedStep}; steps call the real services so
 * every business rule, audit entry and reference number is produced exactly as in production use.
 */
@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
public class DevDataSeeder implements ApplicationRunner {

    private final AppProperties properties;
    private final VehicleRepository vehicleRepository;
    private final List<SeedStep> steps;
    private final Clock clock;
    private final ZoneId zone;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.seed().enabled()) {
            return;
        }
        if (vehicleRepository.count() > 0) {
            log.info("Seed data skipped: vehicles already exist");
            return;
        }
        long start = System.currentTimeMillis();
        SeedContext context = new SeedContext(clock, zone);
        steps.stream().sorted(Comparator.comparingInt(SeedStep::order)).forEach(step -> {
            long t = System.currentTimeMillis();
            step.seed(context);
            log.info("Seed step '{}' done in {} ms", step.name(), System.currentTimeMillis() - t);
        });
        log.info("Development seed data loaded in {} ms. Login accounts use password '{}'.", System.currentTimeMillis() - start, SeedContext.PASSWORD);
    }
}
