package com.limoz.fleet.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.limoz.fleet.config.AppProperties;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sliding-window throttle on login attempts per (email, client IP) to blunt credential stuffing.
 */
@Service
public class LoginAttemptService {

    private final Cache<String, AtomicInteger> attempts;
    private final int maxAttempts;

    public LoginAttemptService(AppProperties properties) {
        this.maxAttempts = properties.security().login().maxAttemptsPerWindow();
        this.attempts = Caffeine.newBuilder()
                .expireAfterWrite(properties.security().login().window())
                .maximumSize(50_000)
                .build();
    }

    public boolean isBlocked(String key) {
        AtomicInteger count = attempts.getIfPresent(key);
        return count != null && count.get() >= maxAttempts;
    }

    public void recordFailure(String key) {
        attempts.get(key, k -> new AtomicInteger()).incrementAndGet();
    }

    public void reset(String key) {
        attempts.invalidate(key);
    }
}
