package com.eveassist.api.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Configuration
public class ApiConfig {
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("MM-dd-yyyy HH:mm");

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
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
