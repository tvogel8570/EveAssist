package com.eveassist;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Replaces the real ESI client, so that no test reaches the network, and gives costing its adjusted prices. */
@TestConfiguration(proxyBeanMethods = false)
public class TestEsiConfig {

    @Bean
    @Primary
    TestEsi testEsi() {
        return new TestEsi();
    }

    /** Costing's adjusted prices come from the test, since the application has no source of them yet. */
    @Bean
    @Primary
    TestAdjustedPrices testAdjustedPrices() {
        return new TestAdjustedPrices();
    }
}
