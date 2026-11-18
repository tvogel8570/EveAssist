package com.eveassist.market;

import static org.assertj.core.api.Assertions.assertThat;

import com.eveassist.IntegrationTest;
import com.eveassist.esi.EsiResult;
import com.eveassist.esi.MarketOrder;
import com.eveassist.esi.MarketPrice;
import com.eveassist.market.internal.AdjustedPriceRepository;
import com.eveassist.sde.TypeId;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Refreshing and reading CCP's adjusted prices on the real tables, with an ESI that answers whatever the test says. */
class AdjustedPriceServiceIT extends IntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-18T21:00:00Z");
    private static final TypeId TRIT = new TypeId(34);
    private static final TypeId PYERITE = new TypeId(35);
    private static final TypeId ORE = new TypeId(36);

    @Autowired
    AdjustedPriceRepository prices;

    @Autowired
    JdbcClient jdbc;

    /** What the scripted ESI answers next, and how many times it was told to forget its ETag. */
    private final List<EsiResult<List<MarketPrice>>> answers = new ArrayList<>();
    private final AtomicInteger forgotten = new AtomicInteger();

    private final OrdersEsi scripted = new OrdersEsi() {
        @Override
        public EsiResult<List<MarketOrder>> marketOrders(int regionId, int typeId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public EsiResult<List<MarketPrice>> marketPrices() {
            return answers.removeFirst();
        }

        @Override
        public void forgetMarketPrices() {
            forgotten.incrementAndGet();
        }
    };

    private AdjustedPriceService service() {
        return new AdjustedPriceService(prices, scripted, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static MarketPrice price(int type, String adjusted, String average) {
        return new MarketPrice(type, adjusted == null ? null : new BigDecimal(adjusted), average == null ? null : new BigDecimal(average));
    }

    private static EsiResult<List<MarketPrice>> updated(MarketPrice... rows) {
        return EsiResult.updated(List.of(rows), NOW.plusSeconds(3600));
    }

    /** The two tables hold public data shared by every test, so a test that needs them empty empties them. */
    @BeforeEach
    void emptyTables() {
        answers.clear();
        forgotten.set(0);
        jdbc.sql("DELETE FROM adjusted_price").update();
        jdbc.sql("DELETE FROM adjusted_price_fetch").update();
    }

    @Test
    void storesWhatEsiSendsAndKeepsTheDigitsAZeroAndTheMissingFields() {
        answers.add(updated(price(34, "2.9139375060564285", "2.78"), price(35, "0", null), price(36, null, "1.5")));

        var refresh = service().refresh();

        assertThat(refresh.failed()).isFalse();
        assertThat(refresh.asOf()).isEqualTo(NOW);
        var stored = service().of(List.of(TRIT, PYERITE, ORE, new TypeId(999_999)));
        // the digits are kept exactly, an adjusted price of 0 is a price, and one ESI did not send is not there
        assertThat(stored.get(TRIT)).isEqualTo(new BigDecimal("2.9139375060564285"));
        assertThat(stored.get(PYERITE)).isEqualByComparingTo("0");
        assertThat(stored).doesNotContainKeys(ORE, new TypeId(999_999));
        assertThat(service().asOf()).contains(NOW);
    }

    @Test
    void aRefreshReplacesEverythingStoredSoATypeEsiNoLongerListsGoes() {
        answers.add(updated(price(34, "4", null), price(35, "8", null)));
        answers.add(updated(price(36, "7", null)));

        service().refresh();
        service().refresh();

        assertThat(service().of(List.of(TRIT, PYERITE, ORE))).containsOnlyKeys(ORE);
    }

    @Test
    void aFailedRefreshKeepsTheStoredPricesAndSaysWhy() {
        answers.add(updated(price(34, "4", null)));
        service().refresh();
        answers.add(EsiResult.failed("ESI answered 503 for markets/prices"));

        var refresh = service().refresh();

        assertThat(refresh.failed()).isTrue();
        assertThat(refresh.problem()).contains("503");
        assertThat(refresh.asOf()).isEqualTo(NOW);
        assertThat(service().of(List.of(TRIT))).containsOnlyKeys(TRIT);
    }

    @Test
    void unchangedKeepsThePricesAndMovesTheirAge() {
        answers.add(updated(price(34, "4", null)));
        service().refresh();
        jdbc.sql("UPDATE adjusted_price_fetch SET fetched_at = :old").param("old", java.sql.Timestamp.from(NOW.minusSeconds(7200))).update();
        answers.add(EsiResult.unchanged(NOW.plusSeconds(3600)));

        var refresh = service().refresh();

        assertThat(refresh.failed()).isFalse();
        assertThat(refresh.asOf()).isEqualTo(NOW);
        assertThat(service().of(List.of(TRIT))).containsOnlyKeys(TRIT);
        assertThat(forgotten).hasValue(0);
    }

    @Test
    void unchangedWithNothingStoredMakesTheClientForgetAndAsksOnceMore() {
        answers.add(EsiResult.unchanged(NOW.plusSeconds(3600)));
        answers.add(updated(price(34, "4", null)));

        var refresh = service().refresh();

        assertThat(refresh.failed()).isFalse();
        assertThat(forgotten).hasValue(1);
        assertThat(service().of(List.of(TRIT))).containsOnlyKeys(TRIT);
    }

    @Test
    void unchangedTwiceWithNothingStoredIsAProblemAndStoresNothing() {
        answers.add(EsiResult.unchanged(NOW.plusSeconds(3600)));
        answers.add(EsiResult.unchanged(NOW.plusSeconds(3600)));

        var refresh = service().refresh();

        assertThat(refresh.failed()).isTrue();
        assertThat(refresh.asOf()).isNull();
        assertThat(prices.hasAny()).isFalse();
        assertThat(service().asOf()).isEmpty();
    }

    @Test
    void anItemNothingWasAskedForIsNotInTheAnswerAndAnEmptyAskGivesNothing() {
        answers.add(updated(price(34, "4", null)));
        service().refresh();

        assertThat(service().of(List.of())).isEmpty();
        assertThat(service().of(List.of(PYERITE))).isEmpty();
    }
}
