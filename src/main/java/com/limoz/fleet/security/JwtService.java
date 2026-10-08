package com.limoz.fleet.security;

import com.limoz.fleet.config.AppProperties;
import com.limoz.fleet.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtService {

    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_PERMISSIONS = "permissions";
    public static final String CLAIM_NAME = "name";
    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_TOKEN_VERSION = "tv";
    public static final String CLAIM_DRIVER_ID = "driverId";

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final AppProperties properties;
    private final Clock clock;

    public record IssuedToken(String token, Instant expiresAt, String jti) {}

    public IssuedToken issueAccessToken(User user) {
        Instant now = Instant.now(clock);
        Instant expiry = now.plus(properties.security().jwt().accessTokenTtl());
        String jti = UUID.randomUUID().toString();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(properties.security().jwt().issuer())
                .issuedAt(now)
                .expiresAt(expiry)
                .id(jti)
                .subject(String.valueOf(user.getId()))
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_NAME, user.getFullName())
                .claim(CLAIM_ROLES, List.copyOf(user.roleCodes()))
                .claim(CLAIM_PERMISSIONS, List.copyOf(user.permissionCodes()))
                .claim(CLAIM_TOKEN_VERSION, user.getTokenVersion());
        if (user.getDriverId() != null) {
            claims.claim(CLAIM_DRIVER_ID, user.getDriverId());
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return new IssuedToken(token, expiry, jti);
    }

    public Jwt decode(String token) {
        return decoder.decode(token);
    }
}
