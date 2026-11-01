package com.eveassist;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EveAssistApplication {

    public static void main(String[] args) {
        // WSL forwards Windows' 127.0.0.1 only to plain IPv4 listeners, and Java otherwise listens through its IPv6 stack
        System.setProperty("java.net.preferIPv4Stack", "true");
        SpringApplication.run(EveAssistApplication.class, args);
    }
}
