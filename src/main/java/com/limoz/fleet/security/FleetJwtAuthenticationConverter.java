package com.limoz.fleet.security;

import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns a validated JWT into an {@link AuthenticatedUser} principal with role and permission authorities,
 * refusing tokens that were revoked (logout) or issued before the user's credentials changed.
 */
@Component
@RequiredArgsConstructor
public class FleetJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final TokenRevocationService revocationService;
    private final UserStatusCache userStatusCache;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        if (revocationService.isRevoked(jwt.getId())) {
            throw new InvalidBearerTokenException("Token has been revoked");
        }
        Long userId = Long.valueOf(jwt.getSubject());
        UserStatusCache.Status status = userStatusCache.status(userId);
        Integer tokenVersion = jwt.getClaim(JwtService.CLAIM_TOKEN_VERSION) instanceof Number n ? n.intValue() : -1;
        if (!status.active() || status.tokenVersion() != tokenVersion) {
            throw new InvalidBearerTokenException("Token is no longer valid for this account");
        }
        List<String> roles = jwt.getClaimAsStringList(JwtService.CLAIM_ROLES);
        List<String> permissions = jwt.getClaimAsStringList(JwtService.CLAIM_PERMISSIONS);
        Set<String> roleSet = roles == null ? Set.of() : new HashSet<>(roles);
        Set<String> permissionSet = permissions == null ? Set.of() : new HashSet<>(permissions);
        Set<GrantedAuthority> authorities = new HashSet<>();
        roleSet.forEach(r -> authorities.add(new SimpleGrantedAuthority(Roles.authority(r))));
        permissionSet.forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
        Number driverId = jwt.getClaim(JwtService.CLAIM_DRIVER_ID);
        AuthenticatedUser principal = new AuthenticatedUser(userId, jwt.getClaimAsString(JwtService.CLAIM_EMAIL),
                jwt.getClaimAsString(JwtService.CLAIM_NAME), roleSet, permissionSet,
                driverId == null ? null : driverId.longValue());
        return new FleetAuthenticationToken(principal, jwt, authorities);
    }
}
