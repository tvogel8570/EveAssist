package com.eveassist;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EveAssistApplication {

    public static void main(String[] args) {
        // WSL forwards Windows' 127.0.0.1 only to plain IPv4 listeners, and Java otherwise listens through its IPv6 stack
        System.setProperty("java.net.preferIPv4Stack", "true");
        useUtc();
        SpringApplication.run(EveAssistApplication.class, args);
    }

    /**
     * Every time the app shows or stores is UTC (an account's own time zone comes later), so the JVM does not take its
     * zone from the machine: log lines, and anything formatted without naming a zone, agree on every machine.
     */
    static void useUtc() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }
}
