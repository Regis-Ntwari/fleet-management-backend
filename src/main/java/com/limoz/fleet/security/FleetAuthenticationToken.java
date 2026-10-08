package com.limoz.fleet.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;

public class FleetAuthenticationToken extends AbstractAuthenticationToken {

    private final AuthenticatedUser principal;
    private final Jwt jwt;

    public FleetAuthenticationToken(AuthenticatedUser principal, Jwt jwt, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        this.jwt = jwt;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return jwt;
    }

    @Override
    public AuthenticatedUser getPrincipal() {
        return principal;
    }

    public Jwt getJwt() {
        return jwt;
    }

    @Override
    public String getName() {
        return principal.email();
    }
}
