package com.eveassist.characters.internal;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Replaces EVE SSO and the image server, so that no test reaches the network. */
@TestConfiguration(proxyBeanMethods = false)
class TestCharactersConfig {

    @Bean
    @Primary
    FakeEveSso fakeEveSso() {
        return new FakeEveSso();
    }

    @Bean
    @Primary
    FakePortraitSource fakePortraitSource() {
        return new FakePortraitSource();
    }
}
