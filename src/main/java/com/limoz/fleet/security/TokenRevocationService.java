package com.limoz.fleet.security;

import com.limoz.fleet.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Access tokens are short-lived and stateless; logout adds the token id to this revocation list so
 * the token is refused immediately. Entries expire with the token.
 */
@Service
@RequiredArgsConstructor
public class TokenRevocationService {

    private final CacheManager cacheManager;

    public void revoke(String jti, Instant expiresAt) {
        cache().put(jti, expiresAt == null ? Instant.MAX : expiresAt);
    }

    public boolean isRevoked(String jti) {
        return jti != null && cache().get(jti) != null;
    }

    private Cache cache() {
        return cacheManager.getCache(CacheConfig.REVOKED_TOKENS);
    }
}
