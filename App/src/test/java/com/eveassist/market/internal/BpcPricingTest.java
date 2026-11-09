package com.eveassist.market.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.eveassist.market.BpcQuote;
import com.eveassist.sde.TypeId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class BpcPricingTest {

    private static final TypeId TYPE = new TypeId(1000);
    private static final Instant AS_OF = Instant.parse("2026-11-09T06:00:00Z");
    private static final BigDecimal SHARE = new BigDecimal("0.05");

    private static BpcOffer offer(int me, int te, String perRun, long runs) {
        return new BpcOffer(1000, me, te, new BigDecimal(perRun), runs);
    }

    private static java.util.Optional<BpcQuote> quote(int me, int te, BpcOffer... offers) {
        return BpcPricing.quote(TYPE, me, te, List.of(offers), SHARE, AS_OF);
    }

    @Test
    void usesCopiesWithExactlyTheMeAndTeWhenThereAreAny() {
        var quote = quote(10, 20, offer(10, 20, "500.00", 10), offer(10, 20, "900.00", 10), offer(2, 4, "100.00", 10));

        assertThat(quote).hasValueSatisfying(q -> {
            assertThat(q.match()).isEqualTo(BpcQuote.Match.EXACT);
            assertThat(q.perRun()).isEqualByComparingTo("500.00");
            assertThat(q.offers()).isEqualTo(2);
            assertThat(q.runsOffered()).isEqualTo(20);
        });
    }

    @Test
    void fallsBackToCopiesAtLeastAsGoodAndSaysSo() {
        var quote = quote(5, 10, offer(10, 20, "700.00", 10), offer(5, 8, "100.00", 10), offer(10, 8, "100.00", 10));

        assertThat(quote).hasValueSatisfying(q -> {
            assertThat(q.match()).isEqualTo(BpcQuote.Match.AT_LEAST);
            assertThat(q.perRun()).isEqualByComparingTo("700.00");
        });
    }

    @Test
    void hasNoPriceWhenNothingIsAsGood() {
        assertThat(quote(10, 20, offer(2, 4, "100.00", 10))).isEmpty();
        assertThat(quote(10, 20)).isEmpty();
    }

    @Test
    void weightsTheCheapestRunsLikeTheMarketRule() {
        // 5% of 1000 runs is 50 runs: 10 at 100 and 40 at 200 average to 180
        var quote = quote(0, 0, offer(0, 0, "100.00", 10), offer(0, 0, "200.00", 200), offer(0, 0, "300.00", 790));

        assertThat(quote).hasValueSatisfying(q -> assertThat(q.perRun()).isEqualByComparingTo("180.00"));
    }

    @Test
    void leavesOutOffersTenTimesAboveOrBelowTheMiddleOne() {
        var quote = quote(0, 0, offer(0, 0, "1.00", 1), offer(0, 0, "1000.00", 1), offer(0, 0, "1100.00", 1),
                offer(0, 0, "1200.00", 1), offer(0, 0, "9000000.00", 1));

        assertThat(quote).hasValueSatisfying(q -> {
            assertThat(q.offers()).isEqualTo(3);
            assertThat(q.perRun()).isEqualByComparingTo("1000.00");
        });
    }

    @Test
    void keepsEveryOfferWhenThereAreTooFewToJudge() {
        var quote = quote(0, 0, offer(0, 0, "1.00", 1), offer(0, 0, "9000.00", 1));

        assertThat(quote).hasValueSatisfying(q -> assertThat(q.offers()).isEqualTo(2));
    }
}
