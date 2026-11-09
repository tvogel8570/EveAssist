package com.eveassist.characters.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Talks to EVE SSO with the authorization code flow and the application's secret (HTTP Basic), as the EVE
 * documentation describes. The JWKS address is read from SSO's metadata and kept for an hour, because the
 * documentation says it is not guaranteed to stay the same.
 */
@Component
class HttpEveSso implements EveSso {

    private static final Logger log = LoggerFactory.getLogger(HttpEveSso.class);
    private static final Duration JWKS_CACHE_TIME = Duration.ofHours(1);

    private final RestClient http;
    private final EveSsoProperties properties;
    private final EveTokenValidator validator;
    private final Clock clock;
    private JwtDecoder decoder;
    private Instant decoderLoadedAt = Instant.MIN;

    HttpEveSso(RestClient.Builder builder, EveSsoProperties properties, Clock clock) {
        this.http = builder.build();
        this.properties = properties;
        this.clock = clock;
        this.validator = new EveTokenValidator(this::jwtDecoder, properties.clientId());
    }

    @Override
    public String authorizeUrl(String state, Set<String> scopes) {
        return UriComponentsBuilder.fromUriString(properties.authorizeUrl())
                .queryParam("response_type", "code")
                .queryParam("client_id", properties.clientId())
                .queryParam("redirect_uri", properties.redirectUri())
                .queryParam("scope", String.join(" ", scopes.stream().sorted().toList()))
                .queryParam("state", state)
                .encode()
                .build()
                .toUriString();
    }

    @Override
    public SsoLink exchange(String code) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        TokenResponse response;
        try {
            response = http.post().uri(properties.tokenUrl())
                    .headers(headers -> headers.setBasicAuth(properties.clientId(), properties.clientSecret()))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
        } catch (RestClientException e) {
            throw new SsoException("EVE SSO refused the authorization code or did not answer", e);
        }
        if (response == null || response.accessToken() == null || response.refreshToken() == null) {
            throw new SsoException("EVE SSO answered without tokens");
        }
        return new SsoLink(validator.validate(response.accessToken()), response.refreshToken());
    }

    @Override
    public RefreshedAccess refresh(String refreshToken) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        TokenResponse response;
        try {
            response = http.post().uri(properties.tokenUrl())
                    .headers(headers -> headers.setBasicAuth(properties.clientId(), properties.clientSecret()))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
        } catch (RestClientResponseException e) {
            // EVE answers 400 with {"error":"invalid_grant"} for a refresh token it refuses
            if (e.getStatusCode().value() == 400 && e.getResponseBodyAsString().contains("invalid_grant")) {
                throw new InvalidGrantException("EVE SSO refused the refresh token", e);
            }
            throw new SsoException("EVE SSO answered " + e.getStatusCode().value() + " to a refresh", e);
        } catch (RestClientException e) {
            throw new SsoException("EVE SSO did not answer a refresh", e);
        }
        if (response == null || response.accessToken() == null) {
            throw new SsoException("EVE SSO answered a refresh without an access token");
        }
        // EVE may return the same refresh token or a new one. The newest is the one to keep.
        var newest = response.refreshToken() != null ? response.refreshToken() : refreshToken;
        return new RefreshedAccess(response.accessToken(), validator.validate(response.accessToken()), newest);
    }

    /**
     * {@code POST} to the revocation endpoint that EVE SSO's metadata advertises (the documentation pages do not
     * describe it), authenticated like the token endpoint. The token goes in the body and nowhere else, and is never
     * logged.
     */
    @Override
    public boolean revoke(String refreshToken) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("token_type_hint", "refresh_token");
        form.add("token", refreshToken);
        try {
            http.post().uri(properties.revokeUrl())
                    .headers(headers -> headers.setBasicAuth(properties.clientId(), properties.clientSecret()))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            log.warn("EVE SSO did not confirm a token revocation: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    private synchronized JwtDecoder jwtDecoder() {
        var now = clock.instant();
        if (decoder == null || Duration.between(decoderLoadedAt, now).compareTo(JWKS_CACHE_TIME) > 0) {
            Metadata metadata;
            try {
                metadata = http.get().uri(properties.metadataUrl()).retrieve().body(Metadata.class);
            } catch (RestClientException e) {
                throw new SsoException("Could not read EVE SSO's metadata", e);
            }
            if (metadata == null || metadata.jwksUri() == null) {
                throw new SsoException("EVE SSO's metadata has no JWKS address");
            }
            decoder = NimbusJwtDecoder.withJwkSetUri(metadata.jwksUri()).build();
            decoderLoadedAt = now;
        }
        return decoder;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokenResponse(@JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Metadata(@JsonProperty("jwks_uri") String jwksUri) {
    }
}
