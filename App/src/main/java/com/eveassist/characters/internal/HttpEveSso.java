package com.eveassist.characters.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Talks to EVE SSO with the authorization code flow and the application's secret (HTTP Basic), as the EVE
 * documentation describes. The JWKS address is read from SSO's metadata and kept for an hour, because the
 * documentation says it is not guaranteed to stay the same.
 */
@Component
class HttpEveSso implements EveSso {

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
