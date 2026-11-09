package com.eveassist.market.internal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Starts {@link ContractLoader} a minute after start-up and then 30 minutes after each run ends, which is how long ESI
 * caches the contract list. Waiting for the last run to end, not a fixed clock time, is what ESI's best practices ask
 * for. Switched off with {@code eveassist.market.contracts.background=false}, as tests do.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "eveassist.market.contracts.background", havingValue = "true", matchIfMissing = true)
class ContractLoadSchedule {

    private final ContractLoader loader;

    ContractLoadSchedule(ContractLoader loader) {
        this.loader = loader;
    }

    @Scheduled(initialDelayString = "${eveassist.market.contracts.initial-delay:PT1M}",
            fixedDelayString = "${eveassist.market.contracts.interval:PT30M}")
    void run() {
        loader.loadAll();
    }
}
