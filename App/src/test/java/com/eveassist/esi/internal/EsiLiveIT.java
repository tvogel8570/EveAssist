package com.eveassist.esi.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.eveassist.esi.EsiResult;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.web.client.RestClient;

/**
 * Asks the real ESI for Tritanium orders in The Forge, to check the client against what ESI really sends. It only runs
 * when asked, so that the build never depends on the network: ./mvnw verify -Dit.test=EsiLiveIT -Deveassist.esi-live=true
 */
@EnabledIfSystemProperty(named = "eveassist.esi-live", matches = "true")
class EsiLiveIT {

    @Test
    void readsTritaniumOrdersInTheForge() {
        var stored = new HashMap<String, EsiCache.Entry>();
        EsiCache cache = new EsiCache() {
            @Override
            public Optional<Entry> find(String key) {
                return Optional.ofNullable(stored.get(key));
            }

            @Override
            public void save(String key, Entry entry, Instant fetchedAt) {
                stored.put(key, entry);
            }

            @Override
            public void forgetPrefix(String keyPrefix) {
                stored.keySet().removeIf(key -> key.startsWith(keyPrefix));
            }
        };
        var properties = new EsiProperties("https://esi.evetech.net", System.getProperty("eveassist.esi-contact", ""), "2026-08-18", 8);
        var client = new HttpEsiClient(RestClient.builder(), properties, cache,
                new EsiLimiter(Clock.systemUTC(), millis -> { }), Clock.systemUTC());

        var result = client.marketOrders(10000002, 34);
        assertThat(result.outcome()).isEqualTo(EsiResult.Outcome.UPDATED);
        assertThat(result.data()).isNotEmpty();
        assertThat(result.data()).allSatisfy(order -> {
            assertThat(order.typeId()).isEqualTo(34);
            assertThat(order.price()).isPositive();
        });
        assertThat(result.expires()).isAfter(Instant.now().minusSeconds(60));

        // Straight away, the stored copy is still valid, so ESI is not asked again
        assertThat(client.marketOrders(10000002, 34).outcome()).isEqualTo(EsiResult.Outcome.UNCHANGED);
    }

    /** The whole price list, as ESI really sends it: thousands of rows, adjusted prices of 0 among them, some with no average. */
    @Test
    void readsTheMarketPricesAndAsksOnlyOnce() {
        var stored = new HashMap<String, EsiCache.Entry>();
        EsiCache cache = new EsiCache() {
            @Override
            public Optional<Entry> find(String key) {
                return Optional.ofNullable(stored.get(key));
            }

            @Override
            public void save(String key, Entry entry, Instant fetchedAt) {
                stored.put(key, entry);
            }

            @Override
            public void forgetPrefix(String keyPrefix) {
                stored.keySet().removeIf(key -> key.startsWith(keyPrefix));
            }
        };
        var properties = new EsiProperties("https://esi.evetech.net", System.getProperty("eveassist.esi-contact", ""), "2026-08-18", 8);
        var client = new HttpEsiClient(RestClient.builder(), properties, cache,
                new EsiLimiter(Clock.systemUTC(), millis -> { }), Clock.systemUTC());

        var result = client.marketPrices();

        assertThat(result.outcome()).isEqualTo(EsiResult.Outcome.UPDATED);
        assertThat(result.data().size()).isGreaterThan(10_000);
        assertThat(result.data()).filteredOn(price -> price.typeId() == 34).singleElement()
                .satisfies(price -> assertThat(price.adjustedPrice()).isPositive());
        assertThat(result.data()).anySatisfy(price -> assertThat(price.adjustedPrice()).isEqualByComparingTo("0"));
        assertThat(result.data()).anySatisfy(price -> assertThat(price.averagePrice()).isNull());
        assertThat(result.expires()).isAfter(Instant.now());
        // ESI sends Last-Modified for this route. It is not asserted to be in the past: on 2026-11-18 it was 20 minutes ahead of ESI's own
        // Date header, so it is kept as a label and not read as an age (see AdjustedPriceAge)
        assertThat(result.lastModified()).isNotNull();
        System.out.println("market prices read: " + result.data().size() + " rows, last modified " + result.lastModified()
                + ", expires " + result.expires());

        var again = client.marketPrices();
        assertThat(again.outcome()).isEqualTo(EsiResult.Outcome.UNCHANGED);
        assertThat(again.lastModified()).isEqualTo(result.lastModified());
    }
}
