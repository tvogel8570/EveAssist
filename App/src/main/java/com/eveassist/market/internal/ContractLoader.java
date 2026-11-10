package com.eveassist.market.internal;

import com.eveassist.market.ContractRefresh;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
    private final Duration shutdownWait;
    /** Makes starting a run and stopping the loader exclude each other, so a stop never misses a run that is starting. */
    private final Object lifecycle = new Object();
    private volatile boolean stopping;
    private volatile CompletableFuture<Void> currentRun = CompletableFuture.completedFuture(null);

    ContractLoader(ContractPrices prices,
                   @Value("${eveassist.market.contracts.batch-size:500}") int batchSize,
                   @Value("${eveassist.market.contracts.shutdown-wait:PT10S}") Duration shutdownWait) {
        this.prices = prices;
        this.batchSize = batchSize;
        this.shutdownWait = shutdownWait;
    }

    /**
     * Reads batches until nothing is waiting, a batch makes no progress (ESI is throttling or down, and the next run
     * tries again), or the application is stopping. Logs at INFO when the run starts and when it stops, with why and
     * what it did.
     *
     * @return false when a run is already going, so nothing was done
     */
    public boolean loadAll() {
        var done = new CompletableFuture<Void>();
        synchronized (lifecycle) {
            if (stopping) {
                log.info("Public contract loading not started: the application is stopping");
                return false;
            }
            if (!running.compareAndSet(false, true)) {
                log.info("Public contracts are already being read; skipping this run");
                return false;
            }
            currentRun = done;
        }
        var started = System.nanoTime();
        var totals = new Totals();
        var outcome = Outcome.FAILED;
        log.info("Public contract loading started (batches of {})", batchSize);
        try {
            outcome = loop(totals);
            return true;
        } finally {
            log.info("Public contract loading stopped ({}) after {} in {} batches: {} new, {} read, {} gone, {} still to read",
                    outcome.reason, elapsed(System.nanoTime() - started), totals.batches,
                    totals.added, totals.read, totals.gone, totals.pending);
            running.set(false);
            done.complete(null);
        }
    }

    private Outcome loop(Totals totals) {
        while (!stopping) {
            var batch = prices.refresh(ContractPrices.THE_FORGE, batchSize, () -> stopping);
            totals.add(batch);
            log.info("Public contracts: {} new, {} read, {} gone, {} still to read{}", batch.added(), batch.linesRead(),
                    batch.gone(), batch.pending(), batch.problems().isEmpty() ? "" : "; problems: " + batch.problems());
            if (batch.pending() == 0) {
                return Outcome.NOTHING_WAITING;
            }
            if (stopping) {
                return Outcome.APPLICATION_STOPPING;
            }
            if (madeNoProgress(batch)) {
                log.warn("Reading public contracts made no progress; trying again at the next run");
                return Outcome.NO_PROGRESS;
            }
        }
        return Outcome.APPLICATION_STOPPING;
    }

    /** For example {@code 0.4s}, {@code 3m 07s} or {@code 1h 02m 15s}. */
    static String elapsed(long nanos) {
        var time = Duration.ofNanos(nanos);
        if (time.toHours() > 0) {
            return "%dh %02dm %02ds".formatted(time.toHours(), time.toMinutesPart(), time.toSecondsPart());
        }
        if (time.toMinutes() > 0) {
            return "%dm %02ds".formatted(time.toMinutes(), time.toSecondsPart());
        }
        return "%.1fs".formatted(nanos / 1_000_000_000.0);
    }

    private static boolean madeNoProgress(ContractRefresh batch) {
        return batch.linesRead() + batch.gone() == 0;
    }

    /** Why a run ended. {@code FAILED} is what is reported when an exception ends it. */
    private enum Outcome {
        NOTHING_WAITING("all contracts read"),
        NO_PROGRESS("no progress, will try again at the next run"),
        APPLICATION_STOPPING("application stopping"),
        FAILED("failed with an error");

        private final String reason;

        Outcome(String reason) {
            this.reason = reason;
        }
    }

    /** What a run did, added up over its batches. */
    private static final class Totals {
        private int batches;
        private int added;
        private int read;
        private int gone;
        private int pending;

        void add(ContractRefresh batch) {
            batches++;
            added += batch.added();
            read += batch.linesRead();
            gone += batch.gone();
            pending = batch.pending();
        }
    }

    /**
     * Asks the run to stop and waits for it, so that the database pool is still open while it finishes. This bean is
     * destroyed before the pool it depends on. The run stops after the chunk of ESI calls it is in, which takes a few
     * seconds. If it has not stopped within {@code shutdownWait}, shutdown goes on without it: nothing it was doing
     * is half written, because each contract is saved in one transaction.
     */
    @PreDestroy
    void stop() {
        CompletableFuture<Void> run;
        synchronized (lifecycle) {
            stopping = true;
            run = currentRun;
        }
        if (run.isDone()) {
            return;
        }
        log.info("Application stopping: waiting up to {} for the public contract loader to finish its current chunk",
                elapsed(shutdownWait.toNanos()));
        try {
            run.get(shutdownWait.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("The public contract loader did not stop within {}; shutting down anyway. Contracts it had not saved stay pending",
                    elapsed(shutdownWait.toNanos()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            // the run ended with an error, which it has already logged
        }
    }
}
