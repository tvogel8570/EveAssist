package com.eveassist.characters.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The EVE developer application used to link characters. It is separate from the one Keycloak uses to sign users in
 * with EVE, so signing in never asks for scopes.
 *
 * @param redirectUri must match one of the callback URLs registered with the application
 */
@ConfigurationProperties("eveassist.characters.sso")
record EveSsoProperties(
        @DefaultValue("") String clientId,
        @DefaultValue("") String clientSecret,
        @DefaultValue("") String redirectUri,
        @DefaultValue("https://login.eveonline.com/v2/oauth/authorize") String authorizeUrl,
        @DefaultValue("https://login.eveonline.com/v2/oauth/token") String tokenUrl,
        @DefaultValue("https://login.eveonline.com/.well-known/oauth-authorization-server") String metadataUrl) {
}
