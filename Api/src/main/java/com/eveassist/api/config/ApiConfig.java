package com.eveassist.api.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;

import java.time.format.DateTimeFormatter;

@Configuration
public class ApiConfig {
    public static final String DATETIME_FORMAT = "MM-dd-yyyy HH:mm";
    public static LocalDateTimeSerializer LOCAL_DATETIME_SERIALIZER =
            new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(DATETIME_FORMAT));

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        JavaTimeModule module = new JavaTimeModule();
        module.addSerializer(LOCAL_DATETIME_SERIALIZER);
        return new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .registerModule(module);
    }
}
