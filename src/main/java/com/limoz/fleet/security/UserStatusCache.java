package com.limoz.fleet.security;

import com.limoz.fleet.config.CacheConfig;
import com.limoz.fleet.user.domain.User;
import com.limoz.fleet.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Short-lived cache of account status so that disabling a user or rotating a password takes effect
 * within a minute without hitting the database on every request.
 */
@Service
@RequiredArgsConstructor
public class UserStatusCache {

    private final UserRepository userRepository;

    public record Status(boolean active, int tokenVersion) {}

    @Cacheable(cacheNames = CacheConfig.USER_STATUS, key = "#userId")
    public Status status(Long userId) {
        return userRepository.findById(userId)
                .map(u -> new Status(u.isActive(), u.getTokenVersion()))
                .orElse(new Status(false, -1));
    }

    @CacheEvict(cacheNames = CacheConfig.USER_STATUS, key = "#userId")
    public void evict(Long userId) {
        // handled by annotation
    }

    public boolean isValid(User user) {
        return user.isActive();
    }
}
