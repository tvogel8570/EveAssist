package com.eveassist.client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;

@TestConfiguration(proxyBeanMethods = false)
public class TestClientApplication {

    public static void main(String[] args) {
        SpringApplication.from(ClientApplication::main).with(TestClientApplication.class).run(args);
    }

}
