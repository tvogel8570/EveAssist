package com.eveassist.market;

import static org.assertj.core.api.Assertions.assertThat;

import com.eveassist.IntegrationTest;
import com.eveassist.esi.PublicContract;
import com.eveassist.esi.PublicContractItem;
import com.eveassist.sde.TypeId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ContractPricesIT extends IntegrationTest {

    private static final int BLUEPRINT = 910_001;

    @Autowired
    MarketService market;

    private static PublicContract contract(long id, String type, String price) {
        return new PublicContract(id, type, new BigDecimal(price), 5, Instant.now().minusSeconds(3600),
                Instant.now().plusSeconds(86400), 60003760L, 0.01);
    }

    private static PublicContractItem copy(long record, int type, int me, int te, int runs) {
        return new PublicContractItem(record, type, 1, true, true, me, te, runs);
    }

    @Test
    void readsNewContractsOnceAndPricesACopyPerRun() {
        esi.contract(contract(9_100_001, "item_exchange", "1000000"), List.of(copy(1, BLUEPRINT, 10, 20, 10)));
        esi.contract(contract(9_100_002, "item_exchange", "2000000"), List.of(copy(1, BLUEPRINT, 10, 20, 10)));
        esi.contract(contract(9_100_003, "courier", "5"), List.of());
        esi.contract(contract(9_100_004, "item_exchange", "0"), List.of(copy(1, BLUEPRINT, 10, 20, 10)));

        var first = market.refreshContracts(100);

        assertThat(first.added()).isEqualTo(2);
        assertThat(first.linesRead()).isEqualTo(2);
        assertThat(first.pending()).isZero();
        assertThat(esi.contractItemsAsked()).containsExactlyInAnyOrder(9_100_001L, 9_100_002L);
        assertThat(market.bpcPrice(new TypeId(BLUEPRINT), 10, 20)).hasValueSatisfying(quote -> {
            assertThat(quote.perRun()).isEqualByComparingTo("100000.00");
            assertThat(quote.match()).isEqualTo(BpcQuote.Match.EXACT);
        });

        var second = market.refreshContracts(100);

        assertThat(second.added()).isZero();
        assertThat(esi.contractItemsAsked()).hasSize(2);
    }

    @Test
    void readsOnlyAsManyContractsAsAskedAndLeavesTheRestPending() {
        esi.contract(contract(9_200_001, "item_exchange", "100"), List.of(copy(1, BLUEPRINT + 1, 0, 0, 1)));
        esi.contract(contract(9_200_002, "item_exchange", "100"), List.of(copy(1, BLUEPRINT + 1, 0, 0, 1)));
        esi.contract(contract(9_200_003, "item_exchange", "100"), List.of(copy(1, BLUEPRINT + 1, 0, 0, 1)));

        var refresh = market.refreshContracts(2);

        assertThat(refresh.linesRead()).isEqualTo(2);
        assertThat(refresh.pending()).isEqualTo(1);
        assertThat(market.refreshContracts(2).pending()).isZero();
    }

    @Test
    void dropsContractsEsiHasNoLinesFor() {
        esi.contract(contract(9_300_001, "item_exchange", "100"), List.of());

        var refresh = market.refreshContracts(10);

        assertThat(refresh.gone()).isEqualTo(1);
        assertThat(refresh.pending()).isZero();
        assertThat(market.bpcPrice(new TypeId(BLUEPRINT + 2), 0, 0)).isEmpty();
    }

    @Test
    void valuesOtherItemsInAMixedContractAtTheirJitaPrice() {
        // Tritanium sells at 5.00 in the test ESI, so 100 of it is worth 500 and the copy gets the other 500 for 5 runs
        esi.contract(contract(9_400_001, "item_exchange", "1000"), List.of(copy(1, BLUEPRINT + 3, 0, 0, 5),
                new PublicContractItem(2, 34, 100, true, false, null, null, null)));
        market.currentPrices(List.of(TRITANIUM));

        market.refreshContracts(10);

        assertThat(market.bpcPrice(new TypeId(BLUEPRINT + 3), 0, 0))
                .hasValueSatisfying(quote -> assertThat(quote.perRun()).isEqualByComparingTo("100.00"));
    }
}
