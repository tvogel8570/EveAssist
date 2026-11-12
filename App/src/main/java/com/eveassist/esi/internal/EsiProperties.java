package com.eveassist.esi.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param contactEmail      goes into the User-Agent, which ESI's documentation strongly prefers to carry an email
 * @param compatibilityDate moved forward on purpose, after the app has been checked against ESI's changes
 * @param maxConcurrent     EveAssist's own limit on simultaneous calls, not an ESI rule
 */
@ConfigurationProperties("eveassist.esi")
record EsiProperties(
        @DefaultValue("https://esi.evetech.net") String baseUrl,
        @DefaultValue("") String contactEmail,
        @DefaultValue("2026-08-18") String compatibilityDate,
        @DefaultValue("8") int maxConcurrent) {
}
