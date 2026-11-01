package com.eveassist.identity.internal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.stereotype.Component;

/** EveAssist never calls anything with its Keycloak tokens, so they are not kept after sign-in. */
@Component
class DiscardingAuthorizedClientRepository implements OAuth2AuthorizedClientRepository {

    @Override
    public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(
            String registrationId, Authentication principal, HttpServletRequest request) {
        return null;
    }

    @Override
    public void saveAuthorizedClient(OAuth2AuthorizedClient client, Authentication principal,
            HttpServletRequest request, HttpServletResponse response) {
    }

    @Override
    public void removeAuthorizedClient(String registrationId, Authentication principal,
            HttpServletRequest request, HttpServletResponse response) {
    }
}
