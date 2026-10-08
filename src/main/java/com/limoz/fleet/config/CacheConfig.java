package com.limoz.fleet.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * In-process Caffeine caches. Each cache has an explicit, deliberately short TTL so that
 * dashboards stay fresh while the database is protected from repeated aggregate queries.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String SETTINGS = "settings";
    public static final String REFERENCE_DATA = "referenceData";
    public static final String DASHBOARD = "dashboard";
    public static final String REPORTS = "reports";
    public static final String USER_STATUS = "userStatus";
    public static final String REVOKED_TOKENS = "revokedTokens";

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                cache(SETTINGS, Duration.ofMinutes(10), 500),
                cache(REFERENCE_DATA, Duration.ofMinutes(10), 500),
                cache(DASHBOARD, Duration.ofSeconds(60), 2_000),
                cache(REPORTS, Duration.ofMinutes(2), 500),
                cache(USER_STATUS, Duration.ofSeconds(60), 10_000),
                cache(REVOKED_TOKENS, Duration.ofHours(24), 100_000)));
        return manager;
    }

    private static CaffeineCache cache(String name, Duration ttl, long maxSize) {
        return new CaffeineCache(name, Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(maxSize)
                .recordStats()
                .build());
    }
}
