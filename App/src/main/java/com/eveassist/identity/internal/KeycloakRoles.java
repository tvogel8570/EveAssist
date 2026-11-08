package com.eveassist.identity.internal;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Maps the realm roles in the ID token to authorities. Other Keycloak roles mean nothing to EveAssist. */
final class KeycloakRoles {

    private static final String USER = "USER";
    private static final String ADMIN = "ADMIN";

    private KeycloakRoles() {
    }

    /** Every Keycloak account holds USER by default, but an admin account does not use the industry features. */
    static Set<SimpleGrantedAuthority> authoritiesFrom(Map<String, Object> claims) {
        var roles = realmRoles(claims);
        if (roles.contains(ADMIN)) {
            return Set.of(authority(ADMIN));
        }
        return roles.contains(USER) ? Set.of(authority(USER)) : Set.of();
    }

    private static Collection<?> realmRoles(Map<String, Object> claims) {
        return claims.get("realm_access") instanceof Map<?, ?> access
                && access.get("roles") instanceof Collection<?> roles ? roles : List.of();
    }

    private static SimpleGrantedAuthority authority(String role) {
        return new SimpleGrantedAuthority("ROLE_" + role);
    }
}
