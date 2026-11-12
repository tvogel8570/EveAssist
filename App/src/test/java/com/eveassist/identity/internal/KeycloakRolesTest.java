package com.eveassist.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class KeycloakRolesTest {

    private static Map<String, Object> claims(String... roles) {
        return Map.of("realm_access", Map.of("roles", List.of(roles)));
    }

    @Test
    void userKeepsUser() {
        assertThat(KeycloakRoles.authoritiesFrom(claims("USER", "uma_authorization", "default-roles-eveassist")))
                .containsExactly(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Test
    void adminDoesNotAlsoGetUser() {
        assertThat(KeycloakRoles.authoritiesFrom(claims("ADMIN", "USER")))
                .containsExactly(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    @Test
    void noKnownRoleGivesNoAuthority() {
        assertThat(KeycloakRoles.authoritiesFrom(claims("uma_authorization"))).isEmpty();
    }

    @Test
    void missingClaimGivesNoAuthority() {
        assertThat(KeycloakRoles.authoritiesFrom(Map.of())).isEmpty();
    }
}
