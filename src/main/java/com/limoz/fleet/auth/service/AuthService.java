package com.limoz.fleet.auth.service;

import com.limoz.fleet.auth.domain.RefreshToken;
import com.limoz.fleet.auth.repository.RefreshTokenRepository;

import com.limoz.fleet.audit.domain.AuditAction;
import com.limoz.fleet.audit.service.AuditService;
import com.limoz.fleet.auth.dto.AuthResponse;
import com.limoz.fleet.auth.dto.LoginRequest;
import com.limoz.fleet.common.exception.AuthenticationFailedException;
import com.limoz.fleet.common.exception.TooManyRequestsException;
import com.limoz.fleet.common.util.RequestContext;
import com.limoz.fleet.config.AppProperties;
import com.limoz.fleet.security.JwtService;
import com.limoz.fleet.security.LoginAttemptService;
import com.limoz.fleet.security.TokenRevocationService;
import com.limoz.fleet.settings.domain.SettingKeys;
import com.limoz.fleet.settings.service.SettingsService;
import com.limoz.fleet.user.domain.User;
import com.limoz.fleet.user.mapper.UserMapper;
import com.limoz.fleet.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenRevocationService revocationService;
    private final LoginAttemptService loginAttemptService;
    private final SettingsService settingsService;
    private final AppProperties properties;
    private final AuditService auditService;
    private final UserMapper userMapper;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    public AuthResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        String ip = RequestContext.clientIp().orElse("unknown");
        String attemptKey = email + "|" + ip;
        if (loginAttemptService.isBlocked(attemptKey)) {
            throw new TooManyRequestsException("Too many failed login attempts. Try again later.");
        }
        Instant now = Instant.now(clock);
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) {
            loginAttemptService.recordFailure(attemptKey);
            auditService.recordSystem(AuditAction.LOGIN_FAILED, "User", null, email, "Unknown email");
            throw new AuthenticationFailedException("Invalid credentials");
        }
        if (user.isLocked(now)) {
            throw new LockedException("Account locked");
        }
        if (!user.isActive()) {
            throw new DisabledException("Account disabled");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            loginAttemptService.recordFailure(attemptKey);
            int maxFailures = settingsService.getInt(SettingKeys.SECURITY_MAX_FAILED_LOGINS);
            user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
            if (user.getFailedLoginAttempts() >= maxFailures) {
                user.setLockedUntil(now.plus(Duration.ofMinutes(settingsService.getInt(SettingKeys.SECURITY_LOCKOUT_MINUTES))));
                user.setFailedLoginAttempts(0);
                log.warn("Account {} locked after repeated failed logins from {}", email, ip);
            }
            userRepository.save(user);
            auditService.recordSystem(AuditAction.LOGIN_FAILED, "User", user.getId(), email, "Wrong password");
            throw new AuthenticationFailedException("Invalid credentials");
        }
        loginAttemptService.reset(attemptKey);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        userRepository.save(user);
        auditService.record(AuditAction.LOGIN, "User", user.getId(), user.getEmail(), null, null, "User logged in", user);
        return issue(user, now);
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        Instant now = Instant.now(clock);
        String hash = hash(rawRefreshToken);
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new AuthenticationFailedException("Invalid refresh token"));
        if (!token.isActive(now)) {
            if (token.getRevokedAt() != null && token.getReplacedByHash() != null) {
                // Reuse of a rotated token: possible theft - revoke the whole family for this user.
                log.warn("Refresh token reuse detected for user {}", token.getUserId());
                refreshTokenRepository.revokeAllForUser(token.getUserId(), now);
            }
            throw new AuthenticationFailedException("Refresh token expired or revoked");
        }
        User user = userRepository.findWithRolesById(token.getUserId())
                .orElseThrow(() -> new AuthenticationFailedException("User no longer exists"));
        if (!user.isActive()) {
            throw new DisabledException("Account disabled");
        }
        token.setRevokedAt(now);
        AuthResponse response = issue(user, now);
        token.setReplacedByHash(hash(response.refreshToken()));
        refreshTokenRepository.save(token);
        return response;
    }

    @Transactional
    public void logout(Jwt accessToken, String rawRefreshToken) {
        Instant now = Instant.now(clock);
        if (accessToken != null) {
            revocationService.revoke(accessToken.getId(), accessToken.getExpiresAt());
        }
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokenRepository.findByTokenHash(hash(rawRefreshToken)).ifPresent(t -> {
                t.setRevokedAt(now);
                refreshTokenRepository.save(t);
            });
        } else if (accessToken != null) {
            refreshTokenRepository.revokeAllForUser(Long.valueOf(accessToken.getSubject()), now);
        }
        if (accessToken != null) {
            auditService.record(AuditAction.LOGOUT, "User", Long.valueOf(accessToken.getSubject()),
                    accessToken.getClaimAsString(JwtService.CLAIM_EMAIL), null, null, "User logged out");
        }
    }

    @Transactional
    public void revokeAllSessions(Long userId) {
        refreshTokenRepository.revokeAllForUser(userId, Instant.now(clock));
    }

    @Transactional
    public int purgeExpiredTokens() {
        return refreshTokenRepository.deleteExpiredBefore(Instant.now(clock).minus(Duration.ofDays(7)));
    }

    private AuthResponse issue(User user, Instant now) {
        JwtService.IssuedToken access = jwtService.issueAccessToken(user);
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        String rawRefresh = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        RefreshToken refresh = new RefreshToken();
        refresh.setUserId(user.getId());
        refresh.setTokenHash(hash(rawRefresh));
        refresh.setCreatedAt(now);
        refresh.setExpiresAt(now.plus(properties.security().jwt().refreshTokenTtl()));
        refresh.setIpAddress(RequestContext.clientIp().orElse(null));
        refresh.setUserAgent(RequestContext.userAgent().map(ua -> ua.length() > 255 ? ua.substring(0, 255) : ua).orElse(null));
        refreshTokenRepository.save(refresh);
        long expiresIn = Duration.between(now, access.expiresAt()).toSeconds();
        return new AuthResponse(access.token(), rawRefresh, "Bearer", expiresIn, userMapper.toResponse(user));
    }

    public static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
