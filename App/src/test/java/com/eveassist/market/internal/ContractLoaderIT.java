package com.eveassist.market.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.eveassist.IntegrationTest;
import com.eveassist.esi.PublicContract;
import com.eveassist.esi.PublicContractItem;
import com.eveassist.market.ContractRefresh;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ContractLoaderIT extends IntegrationTest {

    @Autowired
    ContractPrices prices;

    @Autowired
    ContractRepository contracts;

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

        var ran = new ContractLoader(prices, 2).loadAll();

        assertThat(ran).isTrue();
        assertThat(contracts.pendingCount(ContractPrices.THE_FORGE)).isZero();
        assertThat(esi.contractItemsAsked()).hasSize(5);
    }

    @Test
    void doesNothingWhileARunIsGoingAndRunsAgainAfterwards() throws Exception {
        var inside = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var blocking = new ContractPrices(null, null, null, Clock.systemUTC()) {
            @Override
            public ContractRefresh refresh(int regionId, int maxContracts) {
                inside.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return new ContractRefresh(0, 0, 0, 0, List.of());
            }
        };
        var loader = new ContractLoader(blocking, 2);

        var first = Thread.ofVirtual().start(loader::loadAll);
        assertThat(inside.await(10, TimeUnit.SECONDS)).isTrue();

        assertThat(loader.loadAll()).isFalse();

        release.countDown();
        first.join();
        assertThat(loader.loadAll()).isTrue();
    }
}
