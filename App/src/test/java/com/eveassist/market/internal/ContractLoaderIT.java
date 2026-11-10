package com.eveassist.market.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.eveassist.IntegrationTest;
import com.eveassist.esi.PublicContract;
import com.eveassist.esi.PublicContractItem;
import com.eveassist.market.ContractRefresh;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import java.util.HashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;

@ExtendWith(OutputCaptureExtension.class)
class ContractLoaderIT extends IntegrationTest {

    private static final Duration SHUTDOWN_WAIT = Duration.ofSeconds(10);

    @Autowired
    ContractPrices prices;

    @Autowired
    ContractRepository contracts;

    @Autowired
    ConfigurableApplicationContext context;

    private void contract(long id) {
        esi.contract(new PublicContract(id, "item_exchange", new BigDecimal("100"), 5, Instant.now().minusSeconds(60),
                        Instant.now().plusSeconds(86400), 60003760L, 0.01),
                List.of(new PublicContractItem(1, 920_001, 1, true, true, 0, 0, 1)));
    }

    @Test
    void readsBatchAfterBatchUntilNothingIsWaiting() {
        for (long id = 9_600_001; id <= 9_600_005; id++) {
            contract(id);
        }

        var ran = new ContractLoader(prices, 2, SHUTDOWN_WAIT).loadAll();

        assertThat(ran).isTrue();
        assertThat(contracts.pendingCount(ContractPrices.THE_FORGE)).isZero();
        assertThat(esi.contractItemsAsked()).hasSize(5);
    }

    @Test
    void doesNothingWhileARunIsGoingAndRunsAgainAfterwards() throws Exception {
        var inside = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var blocking = new ContractPrices(null, null, null, null, Clock.systemUTC()) {
            @Override
            public ContractRefresh refresh(int regionId, int maxContracts, java.util.function.BooleanSupplier stop) {
                inside.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return new ContractRefresh(0, 0, 0, 0, List.of());
            }
        };
        var loader = new ContractLoader(blocking, 2, SHUTDOWN_WAIT);

        var first = Thread.ofVirtual().start(loader::loadAll);
        assertThat(inside.await(10, TimeUnit.SECONDS)).isTrue();

        assertThat(loader.loadAll()).isFalse();

        release.countDown();
        first.join();
        assertThat(loader.loadAll()).isTrue();
    }

    @Test
    void logsAtInfoWhenARunStartsAndWhenItStopsWithWhy(CapturedOutput output) {
        contract(9_900_001);
        contract(9_900_002);

        new ContractLoader(prices, 500, SHUTDOWN_WAIT).loadAll();

        assertThat(output.getOut())
                .contains("INFO", "Public contract loading started (batches of 500)")
                .contains("Public contract loading stopped (all contracts read)", "in 1 batches");
    }

    @Test
    void showsHowLongARunTookInWords() {
        assertThat(ContractLoader.elapsed(400_000_000L)).isEqualTo("0.4s");
        assertThat(ContractLoader.elapsed(187_000_000_000L)).isEqualTo("3m 07s");
        assertThat(ContractLoader.elapsed(3_735_000_000_000L)).isEqualTo("1h 02m 15s");
    }

    /** A prices stub whose refresh blocks until released and then reports that contracts are still pending. */
    private static ContractPrices blockedUntil(CountDownLatch inside, CountDownLatch release) {
        return new ContractPrices(null, null, null, null, Clock.systemUTC()) {
            @Override
            public ContractRefresh refresh(int regionId, int maxContracts, java.util.function.BooleanSupplier stop) {
                inside.countDown();
                try {
                    release.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return new ContractRefresh(0, 40, 0, 500, List.of());
            }
        };
    }

    @Test
    void shutdownWaitsForTheRunAndTheRunReportsThatTheApplicationWasStopping(CapturedOutput output) throws Exception {
        var inside = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var loader = new ContractLoader(blockedUntil(inside, release), 500, SHUTDOWN_WAIT);
        var run = Thread.ofVirtual().start(loader::loadAll);
        assertThat(inside.await(10, TimeUnit.SECONDS)).isTrue();

        var stopped = new CountDownLatch(1);
        Thread.ofVirtual().start(() -> {
            loader.stop();
            stopped.countDown();
        });

        assertThat(stopped.await(300, TimeUnit.MILLISECONDS)).as("stop waits while the run is going").isFalse();
        release.countDown();
        assertThat(stopped.await(10, TimeUnit.SECONDS)).isTrue();
        run.join();
        assertThat(output.getOut())
                .contains("Application stopping: waiting up to 10.0s")
                .contains("Public contract loading stopped (application stopping)");
        assertThat(loader.loadAll()).as("no run starts after a stop").isFalse();
    }

    @Test
    void shutdownGivesUpWaitingAfterTheTimeoutAndSaysSo(CapturedOutput output) throws Exception {
        var inside = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var loader = new ContractLoader(blockedUntil(inside, release), 500, Duration.ofMillis(200));
        var run = Thread.ofVirtual().start(loader::loadAll);
        assertThat(inside.await(10, TimeUnit.SECONDS)).isTrue();

        loader.stop();

        assertThat(output.getOut()).contains("did not stop within 0.2s; shutting down anyway");
        release.countDown();
        run.join();
    }

    @Test
    void aStopNeverMissesARunThatIsStarting() throws Exception {
        var active = new java.util.concurrent.atomic.AtomicInteger();
        var entries = new java.util.concurrent.atomic.AtomicInteger();
        var prices = new ContractPrices(null, null, null, null, Clock.systemUTC()) {
            @Override
            public ContractRefresh refresh(int regionId, int maxContracts, java.util.function.BooleanSupplier stop) {
                entries.incrementAndGet();
                active.incrementAndGet();
                try {
                    Thread.sleep(2);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    active.decrementAndGet();
                }
                return new ContractRefresh(0, 0, 0, 0, List.of());
            }
        };
        for (int i = 0; i < 200; i++) {
            var loader = new ContractLoader(prices, 500, SHUTDOWN_WAIT);
            var run = Thread.ofPlatform().start(loader::loadAll);

            loader.stop();

            var enteredAtStop = entries.get();
            assertThat(active.get()).as("no run is still going when stop returns (round %d)", i).isZero();
            run.join();
            assertThat(entries.get()).as("no run begins after stop returns (round %d)", i).isEqualTo(enteredAtStop);
        }
    }

    @Test
    void shutdownReturnsAtOnceWhenNothingIsRunning() {
        var started = System.nanoTime();

        new ContractLoader(prices, 500, SHUTDOWN_WAIT).stop();

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(1));
    }

    /**
     * Shutdown waits for the run in the loader's destroy method, which only helps if the database pool is still open
     * then. Spring destroys a bean before the beans it depends on, so the loader must depend on the pool.
     */
    @Test
    void theLoaderIsDestroyedBeforeTheDatabasePool() {
        var beanFactory = context.getBeanFactory();
        var dependents = new HashSet<String>();
        collectDependents(beanFactory, "dataSource", dependents);

        assertThat(dependents).contains("contractLoader");
    }

    private static void collectDependents(org.springframework.beans.factory.config.ConfigurableListableBeanFactory factory,
                                          String bean, Set<String> found) {
        for (var dependent : factory.getDependentBeans(bean)) {
            if (found.add(dependent)) {
                collectDependents(factory, dependent, found);
            }
        }
    }
}
