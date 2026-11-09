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

    /** The SQL of the backfill migration, run on rows as the first flags migration left them: has_bpo false everywhere. */
    @Test
    void theBackfillFlagsContractsWithABlueprintOriginalAndNoOthers() throws Exception {
        var script = new String(getClass().getResourceAsStream("/db/migration/V20261109_1910__bpo-flag-backfill.sql").readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        var withOriginal = 9_950_010L;
        var onlyCopyAndMineral = 9_950_011L;
        for (var id : new long[] {withOriginal, onlyCopyAndMineral}) {
            jdbc.sql("""
                    INSERT INTO contract_public (contract_id, region_id, price, issued_at, expires_at, volume, items_fetched)
                    VALUES (:id, :region, 100, now(), now() + interval '1 day', 0.01, true)
                    """).param("id", id).param("region", REGION + 2).update();
        }
        // an original as real ESI sends it: not a copy, ME and TE present, no runs
        insertLine(withOriginal, 1, 1000, false, 10, 20, null);
        insertLine(withOriginal, 2, 1001, true, 0, 0, 5);
        insertLine(onlyCopyAndMineral, 1, 1001, true, 0, 0, 5);
        insertLine(onlyCopyAndMineral, 2, 34, false, null, null, null);

        jdbc.sql(script).update();

        assertThat(flagged(withOriginal)).isTrue();
        assertThat(flagged(onlyCopyAndMineral)).isFalse();
    }

    private void insertLine(long contract, long record, int type, boolean copy, Integer me, Integer te, Integer runs) {
        jdbc.sql("""
                INSERT INTO contract_public_item (contract_id, record_id, type_id, quantity, included, is_copy, me, te, runs)
                VALUES (:contract, :record, :type, 1, true, :copy, :me, :te, :runs)
                """).param("contract", contract).param("record", record).param("type", type).param("copy", copy)
                .param("me", me, java.sql.Types.SMALLINT).param("te", te, java.sql.Types.SMALLINT)
                .param("runs", runs, java.sql.Types.INTEGER).update();
    }

    private boolean flagged(long contract) {
        return jdbc.sql("SELECT has_bpo FROM contract_public WHERE contract_id = :id").param("id", contract)
                .query(Boolean.class).single();
    }
}
