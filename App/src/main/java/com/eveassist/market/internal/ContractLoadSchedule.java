package com.eveassist.market.internal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Starts {@link ContractLoader} a minute after start-up and then every 30 minutes, which is how long ESI caches the
 * contract list. A run is long on the first load, so it goes on a virtual thread of its own and the scheduler's single
 * thread stays free for other scheduled work. A start that finds a run still going does nothing. Switched off with {@code eveassist.market.contracts.background=false}, as tests do.
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
        Thread.ofVirtual().name("contract-loader").start(loader::loadAll);
    }
}
