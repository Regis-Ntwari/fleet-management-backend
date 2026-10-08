package com.limoz.fleet.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Removes refresh tokens that expired or were revoked more than a week ago. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupJob {

    private final AuthService authService;

    @Scheduled(cron = "${fleet.jobs.token-cleanup-cron}", zone = "${fleet.timezone}")
    public void purge() {
        int removed = authService.purgeExpiredTokens();
        if (removed > 0) {
            log.info("Purged {} expired refresh token(s)", removed);
        }
    }
}
