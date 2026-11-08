package com.eveassist.esi.internal;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(EsiProperties.class)
class EsiConfig {

    @Bean
    EsiLimiter esiLimiter(Clock clock) {
        return new EsiLimiter(clock, millis -> {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }
}
