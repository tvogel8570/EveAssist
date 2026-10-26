package com.eveassist.api.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Configuration
public class ApiConfig {
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("MM-dd-yyyy HH:mm");

    /**
     * Client for ESI, built from Spring Boot's {@code RestClient.Builder} so it shares the auto-configured
     * message converters. Every request sends these headers; callers add per-request ones such as the bearer token.
     * <p>
     * Bodies are buffered so requests carry a Content-Length: ESI rejects chunked POST bodies (such as
     * /characters/affiliation) with "too few items", and the JDK HttpClient streams them chunked by default.
     */
    @Bean
    public RestClient esiRestClient(RestClient.Builder builder) {
        return builder
                .bufferContent((uri, method) -> true)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.CACHE_CONTROL, "no-cache")
                .defaultHeader(HttpHeaders.USER_AGENT, "EveAssist/0.1 Where Am I")
                .build();
    }

    /**
     * Customizes Spring Boot's auto-configured Jackson 3 {@code JsonMapper} (used for API responses)
     * instead of replacing it: omit null properties and write LocalDateTime as MM-dd-yyyy HH:mm.
     */
    @Bean
    public JsonMapperBuilderCustomizer eveAssistJsonCustomizer() {
        return builder -> builder
                .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
                .addModule(new SimpleModule("eveassist-datetime")
                        .addSerializer(LocalDateTime.class, new LocalDateTimeFormatSerializer()));
    }

    static final class LocalDateTimeFormatSerializer extends ValueSerializer<LocalDateTime> {
        @Override
        public void serialize(LocalDateTime value, JsonGenerator gen, SerializationContext ctxt) {
            gen.writeString(DATETIME_FORMAT.format(value));
        }
    }
}
