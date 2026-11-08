package com.eveassist.identity.internal;

import java.util.Optional;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

/** Runs at each sign-in: creates the account on the first one, and sets the authorities from the ID token. */
@Service
class KeycloakUserService extends OidcUserService {

    private final AccountService accounts;

    KeycloakUserService(AccountService accounts) {
        this.accounts = accounts;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest request) {
        var user = super.loadUser(request);
        accounts.signedIn(user.getSubject(), displayNameOf(user));
        return new DefaultOidcUser(
                KeycloakRoles.authoritiesFrom(user.getIdToken().getClaims()),
                user.getIdToken(), user.getUserInfo(), "sub");
    }

    private static String displayNameOf(OidcUser user) {
        return Optional.ofNullable(user.getFullName())
                .filter(name -> !name.isBlank())
                .orElse(user.getPreferredUsername());
    }
}
