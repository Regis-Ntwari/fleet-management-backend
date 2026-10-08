package com.limoz.fleet.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Single source of "now". Business-day calculations use the configured operational zone (Africa/Kigali);
 * all persisted timestamps are UTC instants.
 */
@Configuration
public class ClockConfig {

    @Bean
    public ZoneId operationalZone(AppProperties properties) {
        return ZoneId.of(properties.timezone());
    }

    @Bean
    public Clock clock(ZoneId operationalZone) {
        return Clock.system(operationalZone);
    }
}
