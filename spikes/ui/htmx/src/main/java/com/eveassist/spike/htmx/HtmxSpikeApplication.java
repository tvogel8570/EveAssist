package com.eveassist.spike.htmx;

import com.eveassist.spike.plan.PlanState;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.context.annotation.SessionScope;

@SpringBootApplication
public class HtmxSpikeApplication {

    public static void main(String[] args) {
        SpringApplication.run(HtmxSpikeApplication.class, args);
    }

    /** One plan per browser session. */
    @Bean
    @SessionScope
    public PlanState planState() {
        return new PlanState();
    }
}
