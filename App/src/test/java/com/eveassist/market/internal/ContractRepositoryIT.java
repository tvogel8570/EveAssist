package com.eveassist.market.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eveassist.IntegrationTest;
import com.eveassist.esi.PublicContract;
import com.eveassist.esi.PublicContractItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** What is stored when the application stops in the middle of a write. */
class ContractRepositoryIT extends IntegrationTest {

    private static final int REGION = 10000099;

    @Autowired
    ContractRepository contracts;

    @Autowired
    JdbcClient jdbc;

    @Test
    void aContractWhoseLinesFailHalfWayKeepsNoLinesAndStaysPending() {
        var id = 9_950_001L;
        contracts.sync(REGION, List.of(new PublicContract(id, "item_exchange", new BigDecimal("100"), 5,
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(86400), 60003760L, 0.01)), Instant.now());
        var good = new PublicContractItem(1, 1000, 1, true, true, 0, 0, 1);
        var bad = new PublicContractItem(2, 1000, 1, true, true, 100_000, 0, 1); // ME does not fit a smallint

        assertThatThrownBy(() -> contracts.saveItems(id, List.of(good, bad), new ContractFlags(false, false)))
                .isInstanceOf(Exception.class);

        var lines = jdbc.sql("SELECT count(*) FROM contract_public_item WHERE contract_id = :id")
                .param("id", id).query(Integer.class).single();
        assertThat(lines).isZero();
        assertThat(contracts.pending(REGION, 10)).contains(id);
    }

    @Test
    void syncingTheListTwiceChangesNothingTheSecondTime() {
        var contract = new PublicContract(9_950_002L, "item_exchange", new BigDecimal("100"), 5,
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(86400), 60003760L, 0.01);

        assertThat(contracts.sync(REGION + 1, List.of(contract), Instant.now())).isEqualTo(1);
        assertThat(contracts.sync(REGION + 1, List.of(contract), Instant.now())).isZero();
    }
}
