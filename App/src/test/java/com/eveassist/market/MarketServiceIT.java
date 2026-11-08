package com.eveassist.market;

import static org.assertj.core.api.Assertions.assertThat;

import com.eveassist.IntegrationTest;
import com.eveassist.esi.EsiClient;
import com.eveassist.esi.EsiResult;
import com.eveassist.esi.MarketOrder;
import com.eveassist.market.internal.OrderRepository;
import com.eveassist.sde.TypeId;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** currentPrices on the real database, with an ESI that answers whatever the test says. */
class MarketServiceIT extends IntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-08T21:00:00Z");
    private static final long JITA = 60003760L;
    private static final long ELSEWHERE = 60000004L;

    @Autowired
    OrderRepository orders;

    private final Map<Integer, EsiResult<List<MarketOrder>>> answers = new ConcurrentHashMap<>();
    private final EsiClient answering = (region, type) -> answers.getOrDefault(type, EsiResult.failed("no answer for " + type));

    private MarketService service() {
        return new MarketService(orders, answering, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static MarketOrder order(int type, long id, long location, boolean buy, String price, long volume) {
        return new MarketOrder(id, type, location, buy, new BigDecimal(price), volume);
    }

    @Test
    void worksOutSellAndBuyPricesFromTheOrdersAtJitaOnly() {
        var type = 900_001;
        answers.put(type, EsiResult.updated(List.of(
                order(type, 1, JITA, false, "10.00", 100),
                order(type, 2, JITA, false, "20.00", 100),
                order(type, 3, JITA, true, "8.00", 100),
                order(type, 4, ELSEWHERE, false, "1.00", 100_000)), NOW.plusSeconds(300)));

        var quotes = service().currentPrices(List.of(new TypeId(type)));

        assertThat(quotes.failed()).isEmpty();
        var quote = quotes.quotes().get(new TypeId(type));
        assertThat(quote.sell()).isEqualByComparingTo("10.00");
        assertThat(quote.buy()).isEqualByComparingTo("8.00");
        assertThat(quote.asOf()).isEqualTo(NOW);
    }

    @Test
    void aSideWithNoOrdersHasNoPrice() {
        var type = 900_002;
        answers.put(type, EsiResult.updated(List.of(order(type, 10, JITA, false, "10.00", 100)), NOW.plusSeconds(300)));

        var quote = service().currentPrices(List.of(new TypeId(type))).quotes().get(new TypeId(type));

        assertThat(quote.sell()).isEqualByComparingTo("10.00");
        assertThat(quote.buy()).isNull();
    }

    @Test
    void anItemWithNoOrdersAtJitaIsStillQuotedWithNoPrices() {
        var type = 900_003;
        answers.put(type, EsiResult.updated(List.of(order(type, 20, ELSEWHERE, false, "10.00", 100)), NOW.plusSeconds(300)));

        var quote = service().currentPrices(List.of(new TypeId(type))).quotes().get(new TypeId(type));

        assertThat(quote.sell()).isNull();
        assertThat(quote.buy()).isNull();
        assertThat(quote.asOf()).isEqualTo(NOW);
    }

    @Test
    void newOrdersReplaceTheOldOnes() {
        var type = 900_004;
        answers.put(type, EsiResult.updated(List.of(order(type, 30, JITA, false, "10.00", 100)), NOW.plusSeconds(300)));
        service().currentPrices(List.of(new TypeId(type)));

        answers.put(type, EsiResult.updated(List.of(order(type, 31, JITA, false, "30.00", 100)), NOW.plusSeconds(300)));
        var quote = service().currentPrices(List.of(new TypeId(type))).quotes().get(new TypeId(type));

        assertThat(quote.sell()).isEqualByComparingTo("30.00");
    }

    @Test
    void unchangedKeepsTheStoredOrdersAndConfirmsTheTime() {
        var type = 900_005;
        answers.put(type, EsiResult.updated(List.of(order(type, 40, JITA, false, "10.00", 100)), NOW.minusSeconds(60)));
        new MarketService(orders, answering, Clock.fixed(NOW.minusSeconds(3600), ZoneOffset.UTC))
                .currentPrices(List.of(new TypeId(type)));

        answers.put(type, EsiResult.unchanged(NOW.plusSeconds(300)));
        var quote = service().currentPrices(List.of(new TypeId(type))).quotes().get(new TypeId(type));

        assertThat(quote.sell()).isEqualByComparingTo("10.00");
        assertThat(quote.asOf()).isEqualTo(NOW);
    }

    @Test
    void anItemEsiCouldNotAnswerIsReportedAndLeftOutOfTheQuotes() {
        var good = 900_006;
        var bad = 900_007;
        answers.put(good, EsiResult.updated(List.of(order(good, 50, JITA, false, "10.00", 100)), NOW.plusSeconds(300)));
        answers.put(bad, EsiResult.failed("ESI answered 503"));

        var quotes = service().currentPrices(List.of(new TypeId(good), new TypeId(bad)));

        assertThat(quotes.quotes()).containsOnlyKeys(new TypeId(good));
        assertThat(quotes.failed()).containsEntry(new TypeId(bad), "ESI answered 503");
    }

    @Test
    void aFailureKeepsWhatWasStoredBefore() {
        var type = 900_008;
        answers.put(type, EsiResult.updated(List.of(order(type, 60, JITA, false, "10.00", 100)), NOW.plusSeconds(300)));
        service().currentPrices(List.of(new TypeId(type)));

        answers.put(type, EsiResult.failed("ESI is down"));
        service().currentPrices(List.of(new TypeId(type)));
        answers.put(type, EsiResult.unchanged(NOW.plusSeconds(300)));
        var quote = service().currentPrices(List.of(new TypeId(type))).quotes().get(new TypeId(type));

        assertThat(quote.sell()).isEqualByComparingTo("10.00");
    }

    @Test
    void asksEsiOncePerItemEvenWhenTheItemIsListedTwice() {
        var type = 900_009;
        var calls = new HashMap<Integer, Integer>();
        EsiClient counting = (region, id) -> {
            synchronized (calls) {
                calls.merge(id, 1, Integer::sum);
            }
            return EsiResult.unchanged(NOW.plusSeconds(300));
        };

        new MarketService(orders, counting, Clock.fixed(NOW, ZoneOffset.UTC))
                .currentPrices(List.of(new TypeId(type), new TypeId(type)));

        assertThat(calls).containsEntry(type, 1);
    }
}
