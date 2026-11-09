package com.eveassist.market.internal;

import com.eveassist.market.ContractRefresh;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Reads all of The Forge's public contracts, a batch at a time, until none are waiting. The contracts are public data
 * shared by all users, so this does not need anyone to be signed in (the developer decided this on 2026-11-09). A flag
 * keeps a run that is still going from being joined by a second one. It guards this instance only: with a second
 * instance, two loaders could run at once, and the fix would be to have each batch claim its contracts in the
 * database.
 */
@Component
public class ContractLoader {

    private static final Logger log = LoggerFactory.getLogger(ContractLoader.class);
    private final ContractPrices prices;
    private final int batchSize;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile boolean stopping;

    ContractLoader(ContractPrices prices,
                   @Value("${eveassist.market.contracts.batch-size:500}") int batchSize) {
        this.prices = prices;
        this.batchSize = batchSize;
    }

    /**
     * Reads batches until nothing is waiting, a batch makes no progress (ESI is throttling or down, and the next run
     * tries again), or the application is stopping.
     *
     * @return false when a run is already going, so nothing was done
     */
    public boolean loadAll() {
        if (!running.compareAndSet(false, true)) {
            log.info("Public contracts are already being read; skipping this run");
            return false;
        }
        try {
            loop();
            return true;
        } finally {
            running.set(false);
        }
    }

    private void loop() {
        while (!stopping) {
            var batch = prices.refresh(ContractPrices.THE_FORGE, batchSize);
            log.info("Public contracts: {} new, {} read, {} gone, {} still to read{}", batch.added(), batch.linesRead(),
                    batch.gone(), batch.pending(), batch.problems().isEmpty() ? "" : "; problems: " + batch.problems());
            if (batch.pending() == 0) {
                return;
            }
            if (madeNoProgress(batch)) {
                log.warn("Reading public contracts made no progress; trying again at the next run");
                return;
            }
        }
    }

    private static boolean madeNoProgress(ContractRefresh batch) {
        return batch.linesRead() + batch.gone() == 0;
    }

    @PreDestroy
    void stop() {
        stopping = true;
    }
}
