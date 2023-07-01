package com.eveassist.client;

import com.eveassist.client.pilot.PilotMapper;
import com.eveassist.client.pilot.PilotMapperImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class TestConfig {
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public PilotMapper pilotMapper() {
        return new PilotMapperImpl();
    }
}
