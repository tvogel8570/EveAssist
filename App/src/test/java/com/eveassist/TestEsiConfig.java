package com.eveassist;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Replaces the real ESI client, so that no test reaches the network. */
@TestConfiguration(proxyBeanMethods = false)
public class TestEsiConfig {

    @Bean
    @Primary
    TestEsi testEsi() {
        return new TestEsi();
    }
}
