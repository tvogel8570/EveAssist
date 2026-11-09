package com.eveassist.market.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.eveassist.IntegrationTest;
import com.eveassist.esi.PublicContract;
import com.eveassist.esi.PublicContractItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ContractLoaderIT extends IntegrationTest {

    @Autowired
    ContractPrices prices;

    @Autowired
    ContractRepository contracts;

    @Autowired
    DataSource dataSource;

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

        var ran = new ContractLoader(prices, dataSource, 2).loadAll();

        assertThat(ran).isTrue();
        assertThat(contracts.pendingCount(ContractPrices.THE_FORGE)).isZero();
        assertThat(esi.contractItemsAsked()).hasSize(5);
    }

    @Test
    void doesNothingWhileAnotherRunHoldsTheLock() throws Exception {
        contract(9_700_001);
        try (var other = dataSource.getConnection(); var lock = other.createStatement()) {
            lock.execute("SELECT pg_advisory_lock(" + ContractLoader.LOCK_KEY + ")");
            try {
                assertThat(new ContractLoader(prices, dataSource, 2).loadAll()).isFalse();
                assertThat(esi.contractItemsAsked()).isEmpty();
            } finally {
                lock.execute("SELECT pg_advisory_unlock(" + ContractLoader.LOCK_KEY + ")");
            }
        }

        assertThat(new ContractLoader(prices, dataSource, 2).loadAll()).isTrue();
    }
}
