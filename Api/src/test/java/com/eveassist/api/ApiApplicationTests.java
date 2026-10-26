package com.eveassist.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ApiApplicationTests {
    @Autowired
    RestClient esiRestClient;

    @Test
    void contextLoads() {
        assertThat(esiRestClient).isNotNull();
    }
}
