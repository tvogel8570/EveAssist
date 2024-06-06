package com.eveassist.client.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "pilot")
public record PilotProps(
        String clientId,
        String clientSecret,
        String redirectUri,
        String loginUri,
        String tokenUri,
        String responseType,
        List<String> scope) {

    public String scopeAsString() {
        return String.join(" ", scope);
    }
}
