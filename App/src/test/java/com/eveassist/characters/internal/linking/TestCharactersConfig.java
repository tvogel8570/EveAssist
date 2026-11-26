package com.eveassist.characters.internal.linking;

import com.eveassist.characters.internal.sso.FakeEveSso;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Replaces EVE SSO and the image server, so that no test reaches the network. */
@TestConfiguration(proxyBeanMethods = false)
public class TestCharactersConfig {

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
