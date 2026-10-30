package com.eveassist.spike.react;

import com.eveassist.spike.plan.JobBoard;
import com.eveassist.spike.plan.PlanState;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.context.annotation.SessionScope;

@SpringBootApplication
public class ReactSpikeApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReactSpikeApplication.class, args);
    }

    /** One plan per browser session; the session cookie is the only thing the browser holds. */
    @Bean
    @SessionScope
    public PlanState planState() {
        return new PlanState();
    }

    /** One job board per browser session. */
    @Bean
    @SessionScope
    public JobBoard jobBoard() {
        return new JobBoard();
    }
}
