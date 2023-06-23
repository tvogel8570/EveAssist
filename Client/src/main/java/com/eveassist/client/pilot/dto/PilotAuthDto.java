package com.eveassist.client.pilot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PilotAuthDto(
        @JsonProperty("access_token")
        String accessToken,
        @JsonProperty("expires_in")
        Integer expiresIn,
        @JsonProperty("token_type")
        String tokenType,
        @JsonProperty("refresh_token")
        String refreshToken) {
}
