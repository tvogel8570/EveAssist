package com.eveassist.characters.internal;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Downloads the 128 px portrait from EVE's image server. That server is not ESI and has no token, so nothing about the
 * user goes with the request. A portrait is a nicety: any failure leaves the card with initials.
 */
@Component
class HttpPortraitSource implements PortraitSource {

    private static final Logger log = LoggerFactory.getLogger(HttpPortraitSource.class);
    /** A 128 px JPEG is about 10 KB. Anything much larger is not what was asked for. */
    private static final int MAX_BYTES = 200_000;

    private final RestClient http;

    HttpPortraitSource(RestClient.Builder builder,
            @Value("${eveassist.characters.portrait-url:https://images.evetech.net}") String baseUrl) {
        this.http = builder.baseUrl(baseUrl).build();
    }

    @Override
    public Optional<byte[]> fetch(long eveCharacterId) {
        try {
            var image = http.get().uri("/characters/{id}/portrait?size=128", eveCharacterId)
                    .accept(MediaType.IMAGE_JPEG)
                    .retrieve()
                    .body(byte[].class);
            if (image == null || image.length == 0 || image.length > MAX_BYTES) {
                return Optional.empty();
            }
            return Optional.of(image);
        } catch (RestClientException e) {
            log.warn("No portrait for character {}: {}", eveCharacterId, e.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
