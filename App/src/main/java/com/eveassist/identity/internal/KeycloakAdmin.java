package com.eveassist.identity.internal;

import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Keycloak's admin API, as the one service account that may manage users in this realm and nothing else (see
 * identity-and-tokens.md). Used to delete a user when an account is deleted. In prod the URL is Keycloak's own address on the
 * Compose network, because Caddy answers 404 for {@code /admin} on the public name. Never throws, and never logs the secret or
 * a token.
 */
@Component
public class KeycloakAdmin {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAdmin.class);

    private final RestClient http;
    private final String url;
    private final String realm;
    private final String clientId;
    private final String clientSecret;

    private String accessToken;
    private Instant accessTokenExpiresAt = Instant.MIN;

    KeycloakAdmin(RestClient.Builder http,
            @Value("${eveassist.keycloak-admin.url:}") String url,
            @Value("${eveassist.keycloak-admin.realm:EveAssist}") String realm,
            @Value("${eveassist.keycloak-admin.client-id:eveassist-admin}") String clientId,
            @Value("${eveassist.keycloak-admin.client-secret:}") String clientSecret) {
        this.http = http.build();
        this.url = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        this.realm = realm;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    /** True when the user is gone: deleted now, or not there to begin with. False when Keycloak could not be asked. */
    public boolean deleteUser(String keycloakSub) {
        if (url.isBlank() || clientSecret.isBlank()) {
            log.warn("Keycloak admin access is not configured, so user {} was not deleted from Keycloak", keycloakSub);
            return false;
        }
        try {
            var status = http.delete().uri(url + "/admin/realms/{realm}/users/{id}", realm, keycloakSub)
                    .headers(headers -> headers.setBearerAuth(token()))
                    .exchange((request, response) -> response.getStatusCode().value());
            if (status == 204 || status == 404) {
                return true;
            }
            log.warn("Keycloak answered {} when user {} was deleted", status, keycloakSub);
            return false;
        } catch (RestClientException | IllegalStateException e) {
            log.warn("Keycloak could not be asked to delete user {}: {}", keycloakSub, e.getClass().getSimpleName());
            return false;
        }
    }

    /** The service account's token, kept until shortly before it expires. */
    private synchronized String token() {
        if (accessToken != null && Instant.now().isBefore(accessTokenExpiresAt.minusSeconds(30))) {
            return accessToken;
        }
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        @SuppressWarnings("unchecked")
        Map<String, Object> answer = http.post().uri(url + "/realms/{realm}/protocol/openid-connect/token", realm)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(Map.class);
        if (answer == null || !(answer.get("access_token") instanceof String token)) {
            throw new IllegalStateException("Keycloak gave no access token");
        }
        accessToken = token;
        var lifetime = answer.get("expires_in") instanceof Number seconds ? seconds.longValue() : 60L;
        accessTokenExpiresAt = Instant.now().plusSeconds(lifetime);
        return accessToken;
    }
}
