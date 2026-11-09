package com.eveassist.market.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class BpcOffersTest {

    private static final int BPC_A = 1000;
    private static final int BPC_B = 1001;
    private static final int MINERAL = 34;

    private static ContractLine copy(int type, long quantity, int me, int te, int runs) {
        return new ContractLine(type, quantity, true, true, me, te, runs);
    }

    private static StoredContract contract(String price, ContractLine... lines) {
        return new StoredContract(1, new BigDecimal(price), List.of(lines));
    }

    private static BigDecimal noPrice(int type) {
        return null;
    }

    @Test
    void aSingleCopyCostsThePriceDividedByItsRuns() {
        var offers = BpcOffers.of(contract("1000000.00", copy(BPC_A, 1, 2, 4, 10)), BpcOffersTest::noPrice);

        assertThat(offers).containsExactly(new BpcOffer(BPC_A, 2, 4, new BigDecimal("100000.00"), 10));
    }

    @Test
    void aPackOfCopiesIsSplitByRunsSoEveryRunCostsTheSame() {
        var pack = contract("900.00", copy(BPC_A, 1, 10, 20, 1), copy(BPC_B, 2, 0, 0, 4));

        var offers = BpcOffers.of(pack, BpcOffersTest::noPrice);

        assertThat(offers).containsExactlyInAnyOrder(
                new BpcOffer(BPC_A, 10, 20, new BigDecimal("100.00"), 1),
                new BpcOffer(BPC_B, 0, 0, new BigDecimal("100.00"), 8));
    }

    @Test
    void otherItemsAreTakenAtTheirJitaPriceAndTheCopiesShareTheRest() {
        var mixed = contract("1000.00", copy(BPC_A, 1, 0, 0, 5),
                new ContractLine(MINERAL, 100, true, false, null, null, null));

        var offers = BpcOffers.of(mixed, type -> type == MINERAL ? new BigDecimal("5.00") : null);

        assertThat(offers).singleElement().satisfies(offer -> assertThat(offer.perRun()).isEqualByComparingTo("100.00"));
    }

    @Test
    void skipsContractsThatCannotBePriced() {
        var asksForItems = contract("100.00", copy(BPC_A, 1, 0, 0, 1),
                new ContractLine(MINERAL, 1, false, false, null, null, null));
        var holdsAnOriginal = contract("100.00", copy(BPC_A, 1, 0, 0, 1),
                new ContractLine(BPC_B, 1, true, false, 0, 0, null));
        var unpricedItem = contract("100.00", copy(BPC_A, 1, 0, 0, 1),
                new ContractLine(MINERAL, 1, true, false, null, null, null));
        var cheaperThanTheItems = contract("100.00", copy(BPC_A, 1, 0, 0, 1),
                new ContractLine(MINERAL, 100, true, false, null, null, null));
        var noCopy = contract("100.00", new ContractLine(MINERAL, 1, true, false, null, null, null));

        var jita = (java.util.function.IntFunction<BigDecimal>) type -> type == MINERAL ? new BigDecimal("5.00") : null;

        assertThat(BpcOffers.of(asksForItems, jita)).isEmpty();
        assertThat(BpcOffers.of(holdsAnOriginal, jita)).isEmpty();
        assertThat(BpcOffers.of(unpricedItem, BpcOffersTest::noPrice)).isEmpty();
        assertThat(BpcOffers.of(cheaperThanTheItems, jita)).isEmpty();
        assertThat(BpcOffers.of(noCopy, jita)).isEmpty();
    }
}
