package com.eveassist.market.internal;

import com.eveassist.market.ContractRefresh;
import jakarta.annotation.PreDestroy;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Reads all of The Forge's public contracts, a batch at a time, until none are waiting. The contracts are public data
 * shared by all users, so this does not need anyone to be signed in (the developer decided this on 2026-11-09). A
 * Postgres advisory lock keeps two instances, or a run that is still going, from reading at the same time.
 */
@Component
public class ContractLoader {

    private static final Logger log = LoggerFactory.getLogger(ContractLoader.class);
    /** "EA" and a counter, so the key is easy to find in {@code pg_locks}. */
    static final long LOCK_KEY = 0x4541_0001L;

    private final ContractPrices prices;
    private final DataSource dataSource;
    private final int batchSize;
    private volatile boolean stopping;

    ContractLoader(ContractPrices prices, DataSource dataSource,
                   @Value("${eveassist.market.contracts.batch-size:500}") int batchSize) {
        this.prices = prices;
        this.dataSource = dataSource;
        this.batchSize = batchSize;
    }

    /**
     * Reads batches until nothing is waiting, a batch makes no progress (ESI is throttling or down, and the next run
     * tries again), or the application is stopping.
     *
     * @return false when another run holds the lock, so nothing was done
     */
    public boolean loadAll() {
        try (var connection = dataSource.getConnection()) {
            if (!tryLock(connection)) {
                log.info("Public contracts are already being read; skipping this run");
                return false;
            }
            try {
                loop();
            } finally {
                unlock(connection);
            }
            return true;
        } catch (SQLException e) {
            log.warn("Could not take the lock for reading public contracts: {}", e.getMessage());
            return false;
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

    private static boolean tryLock(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
            statement.setLong(1, LOCK_KEY);
            try (var result = statement.executeQuery()) {
                return result.next() && result.getBoolean(1);
            }
        }
    }

    /** A session lock outlives the pooled connection's return to the pool, so it is released by hand. */
    private static void unlock(Connection connection) {
        try (var statement = connection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
            statement.setLong(1, LOCK_KEY);
            statement.execute();
        } catch (SQLException e) {
            log.warn("Could not release the lock for reading public contracts: {}", e.getMessage());
        }
    }

    @PreDestroy
    void stop() {
        stopping = true;
    }
}
