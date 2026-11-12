package com.eveassist.esi.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.eveassist.esi.EsiResult;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.response.DefaultResponseCreator;
import org.springframework.web.client.RestClient;

class HttpEsiClientTest {

    private static final String PAGE_1 = "https://esi.test/markets/10000002/orders?type_id=34&page=1";
    private static final String PAGE_2 = "https://esi.test/markets/10000002/orders?type_id=34&page=2";
    private static final String ORDERS = """
            [{"duration":90,"is_buy_order":false,"issued":"2026-11-01T00:00:00Z","location_id":60003760,
              "min_volume":1,"order_id":1,"price":5.25,"range":"region","system_id":30000142,"type_id":34,
              "volume_remain":1000,"volume_total":2000},
             {"duration":90,"is_buy_order":true,"issued":"2026-11-01T00:00:00Z","location_id":60003760,
              "min_volume":1,"order_id":2,"price":4.75,"range":"station","system_id":30000142,"type_id":34,
              "volume_remain":500,"volume_total":500}]
            """;

    private final TestClock clock = new TestClock(Instant.parse("2026-11-08T21:00:00Z"));
    private final Map<String, EsiCache.Entry> stored = new HashMap<>();
    private final EsiCache cache = new EsiCache() {
        @Override
        public Optional<Entry> find(String key) {
            return Optional.ofNullable(stored.get(key));
        }

        @Override
        public void save(String key, Entry entry, Instant fetchedAt) {
            stored.put(key, entry);
        }
    };
    private MockRestServiceServer esi;
    private HttpEsiClient client;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder();
        esi = MockRestServiceServer.bindTo(builder).build();
        var properties = new EsiProperties("https://esi.test", "dev@example.com", "2026-08-18", 8);
        client = new HttpEsiClient(builder, properties, cache, new EsiLimiter(clock, millis -> { }), clock);
    }

    @Test
    void readsOrdersAndSendsTheRequiredHeaders() {
        esi.expect(requestTo(PAGE_1))
                .andExpect(header("User-Agent", "EveAssist/dev (dev@example.com; +https://github.com/tvogel8570/EveAssist)"))
                .andExpect(header("X-Compatibility-Date", "2026-08-18"))
                .andRespond(page(ORDERS, "\"abc\"", "Sun, 08 Nov 2026 21:05:00 GMT", "Sun, 08 Nov 2026 21:00:00 GMT"));

        var result = client.marketOrders(10000002, 34);

        assertThat(result.outcome()).isEqualTo(EsiResult.Outcome.UPDATED);
        assertThat(result.expires()).isEqualTo(Instant.parse("2026-11-08T21:05:00Z"));
        assertThat(result.data()).hasSize(2);
        assertThat(result.data().getFirst().price()).isEqualByComparingTo("5.25");
        assertThat(result.data().getFirst().buy()).isFalse();
        assertThat(result.data().get(1).buy()).isTrue();
        assertThat(result.data().getFirst().locationId()).isEqualTo(60003760L);
        assertThat(stored.values()).singleElement().satisfies(entry -> assertThat(entry.etag()).isEqualTo("\"abc\""));
        esi.verify();
    }

    @Test
    void doesNotCallAgainBeforeTheCopyExpires() {
        esi.expect(requestTo(PAGE_1))
                .andRespond(page(ORDERS, "\"abc\"", "Sun, 08 Nov 2026 21:05:00 GMT", "Sun, 08 Nov 2026 21:00:00 GMT"));
        client.marketOrders(10000002, 34);

        clock.advance(Duration.ofMinutes(4));
        var again = client.marketOrders(10000002, 34);

        assertThat(again.outcome()).isEqualTo(EsiResult.Outcome.UNCHANGED);
        esi.verify();
    }

    @Test
    void asksWithTheETagAfterExpiryAndTakesA304AsUnchanged() {
        esi.expect(requestTo(PAGE_1))
                .andRespond(page(ORDERS, "\"abc\"", "Sun, 08 Nov 2026 21:05:00 GMT", "Sun, 08 Nov 2026 21:00:00 GMT"));
        client.marketOrders(10000002, 34);
        clock.advance(Duration.ofMinutes(6));
        esi.verify();
        esi.reset();

        esi.expect(requestTo(PAGE_1)).andExpect(header("If-None-Match", "\"abc\""))
                .andRespond(withStatus(HttpStatus.NOT_MODIFIED).header("Expires", "Sun, 08 Nov 2026 21:11:00 GMT"));
        var result = client.marketOrders(10000002, 34);

        assertThat(result.outcome()).isEqualTo(EsiResult.Outcome.UNCHANGED);
        assertThat(result.expires()).isEqualTo(Instant.parse("2026-11-08T21:11:00Z"));
        assertThat(stored.values()).singleElement().satisfies(entry -> {
            assertThat(entry.etag()).isEqualTo("\"abc\"");
            assertThat(entry.expires()).isEqualTo(Instant.parse("2026-11-08T21:11:00Z"));
        });
        esi.verify();
    }

    @Test
    void joinsPagesThatShareALastModified() {
        esi.expect(requestTo(PAGE_1)).andRespond(page(ORDERS, "\"p1\"", "Sun, 08 Nov 2026 21:05:00 GMT",
                "Sun, 08 Nov 2026 21:00:00 GMT").header("X-Pages", "2"));
        esi.expect(requestTo(PAGE_2)).andRespond(page(ORDERS, "\"p2\"", "Sun, 08 Nov 2026 21:05:00 GMT",
                "Sun, 08 Nov 2026 21:00:00 GMT").header("X-Pages", "2"));

        var result = client.marketOrders(10000002, 34);

        assertThat(result.outcome()).isEqualTo(EsiResult.Outcome.UPDATED);
        assertThat(result.data()).hasSize(4);
        esi.verify();
    }

    @Test
    void readsThePagesAgainOnceWhenTheyChangedWhileReading() {
        esi.expect(requestTo(PAGE_1)).andRespond(page(ORDERS, "\"p1\"", "Sun, 08 Nov 2026 21:05:00 GMT",
                "Sun, 08 Nov 2026 21:00:00 GMT").header("X-Pages", "2"));
        esi.expect(requestTo(PAGE_2)).andRespond(page(ORDERS, "\"p2\"", "Sun, 08 Nov 2026 21:06:00 GMT",
                "Sun, 08 Nov 2026 21:01:00 GMT").header("X-Pages", "2"));
        esi.expect(requestTo(PAGE_1)).andRespond(page(ORDERS, "\"p1\"", "Sun, 08 Nov 2026 21:06:00 GMT",
                "Sun, 08 Nov 2026 21:01:00 GMT").header("X-Pages", "2"));
        esi.expect(requestTo(PAGE_2)).andRespond(page(ORDERS, "\"p2\"", "Sun, 08 Nov 2026 21:06:00 GMT",
                "Sun, 08 Nov 2026 21:01:00 GMT").header("X-Pages", "2"));

        var result = client.marketOrders(10000002, 34);

        assertThat(result.outcome()).isEqualTo(EsiResult.Outcome.UPDATED);
        assertThat(result.data()).hasSize(4);
        esi.verify();
    }

    @Test
    void failsWhenThePagesChangeAgainOnTheSecondRead() {
        for (int read = 0; read < 2; read++) {
            esi.expect(requestTo(PAGE_1)).andRespond(page(ORDERS, "\"p1\"", "Sun, 08 Nov 2026 21:05:00 GMT",
                    "Sun, 08 Nov 2026 21:00:00 GMT").header("X-Pages", "2"));
            esi.expect(requestTo(PAGE_2)).andRespond(page(ORDERS, "\"p2\"", "Sun, 08 Nov 2026 21:06:00 GMT",
                    "Sun, 08 Nov 2026 21:01:00 GMT").header("X-Pages", "2"));
        }

        var result = client.marketOrders(10000002, 34);

        assertThat(result.outcome()).isEqualTo(EsiResult.Outcome.FAILED);
        assertThat(stored).isEmpty();
        esi.verify();
    }

    @Test
    void stopsCallingAfterA429WithALongRetryAfter() {
        esi.expect(requestTo(PAGE_1)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "60"));

        var first = client.marketOrders(10000002, 34);
        var second = client.marketOrders(10000002, 35);

        assertThat(first.outcome()).isEqualTo(EsiResult.Outcome.FAILED);
        assertThat(second.outcome()).isEqualTo(EsiResult.Outcome.FAILED);
        assertThat(second.problem()).contains("throttling");
        esi.verify();
    }

    @Test
    void reportsAServerErrorWithoutRetryingOrCaching() {
        esi.expect(requestTo(PAGE_1)).andRespond(withServerError());

        var result = client.marketOrders(10000002, 34);

        assertThat(result.outcome()).isEqualTo(EsiResult.Outcome.FAILED);
        assertThat(result.problem()).contains("500");
        assertThat(stored).isEmpty();
        esi.verify();
    }

    @Test
    void fallsBackToMaxAgeWhenThereIsNoExpiresHeader() {
        esi.expect(requestTo(PAGE_1)).andRespond(withSuccess(ORDERS, MediaType.APPLICATION_JSON)
                .header("Cache-Control", "public, max-age=120").header("ETag", "\"abc\""));

        var result = client.marketOrders(10000002, 34);

        assertThat(result.expires()).isEqualTo(Instant.parse("2026-11-08T21:02:00Z"));
    }

    @Test
    void readsPublicContractsAcrossPages() {
        var body = """
                [{"contract_id":9,"type":"item_exchange","price":1250000.5,"issuer_id":5,"issuer_corporation_id":6,
                  "date_issued":"2026-11-08T10:00:00Z","date_expired":"2026-11-22T10:00:00Z",
                  "start_location_id":60003760,"volume":0.02,"for_corporation":false}]
                """;
        esi.expect(requestTo("https://esi.test/contracts/public/10000002?page=1"))
                .andRespond(page(body, "\"c1\"", "Sun, 08 Nov 2026 21:30:00 GMT", "Sun, 08 Nov 2026 21:00:00 GMT")
                        .header("X-Pages", "2"));
        esi.expect(requestTo("https://esi.test/contracts/public/10000002?page=2"))
                .andRespond(page("[]", "\"c2\"", "Sun, 08 Nov 2026 21:30:00 GMT", "Sun, 08 Nov 2026 21:00:00 GMT"));

        var result = client.publicContracts(10000002);

        assertThat(result.outcome()).isEqualTo(EsiResult.Outcome.UPDATED);
        assertThat(result.data()).singleElement().satisfies(contract -> {
            assertThat(contract.contractId()).isEqualTo(9);
            assertThat(contract.isItemExchange()).isTrue();
            assertThat(contract.price()).isEqualByComparingTo("1250000.5");
            assertThat(contract.startLocationId()).isEqualTo(60003760L);
            assertThat(contract.dateExpired()).isEqualTo(Instant.parse("2026-11-22T10:00:00Z"));
        });
        assertThat(stored).containsKey("contracts/public/10000002");
        esi.verify();
    }

    @Test
    void readsContractItemsWithBlueprintDetailsAndDoesNotCacheThem() {
        var body = """
                [{"record_id":1,"type_id":1000,"quantity":1,"is_included":true,"is_blueprint_copy":true,
                  "material_efficiency":10,"time_efficiency":20,"runs":5},
                 {"record_id":2,"type_id":34,"quantity":100,"is_included":true}]
                """;
        esi.expect(requestTo("https://esi.test/contracts/public/items/9?page=1"))
                .andRespond(page(body, "\"i\"", "Sun, 08 Nov 2026 22:00:00 GMT", "Sun, 08 Nov 2026 21:00:00 GMT"));

        var result = client.publicContractItems(9);

        assertThat(result.data()).hasSize(2);
        assertThat(result.data().getFirst().blueprintCopy()).isTrue();
        assertThat(result.data().getFirst().materialEfficiency()).isEqualTo(10);
        assertThat(result.data().getFirst().runs()).isEqualTo(5);
        assertThat(result.data().get(1).runs()).isNull();
        assertThat(stored).isEmpty();
        esi.verify();
    }

    @Test
    void takesA204ForContractItemsAsAContractThatIsGone() {
        esi.expect(requestTo("https://esi.test/contracts/public/items/9?page=1"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT).header("Expires", "Sun, 08 Nov 2026 22:00:00 GMT"));

        var result = client.publicContractItems(9);

        assertThat(result.outcome()).isEqualTo(EsiResult.Outcome.UPDATED);
        assertThat(result.data()).isEmpty();
        esi.verify();
    }

    @Test
    void takesA403ForContractItemsAsAContractAcceptedByAPlayer() {
        esi.expect(requestTo("https://esi.test/contracts/public/items/9?page=1"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"Contract accepted by player, requires authorization\"}"));

        var result = client.publicContractItems(9);

        assertThat(result.outcome()).isEqualTo(EsiResult.Outcome.UPDATED);
        assertThat(result.data()).isEmpty();
    }

    @Test
    void takesA200WithNoBodyForContractItemsAsAContractWithNoLines() {
        // seen on real ESI for contracts that are still in the region's list: 200, content-length 0
        esi.expect(requestTo("https://esi.test/contracts/public/items/9?page=1"))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        var result = client.publicContractItems(9);

        assertThat(result.outcome()).isEqualTo(EsiResult.Outcome.UPDATED);
        assertThat(result.data()).isEmpty();
    }

    @Test
    void stillFailsOnA403ForOtherRoutes() {
        esi.expect(requestTo(PAGE_1)).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThat(client.marketOrders(10000002, 34).outcome()).isEqualTo(EsiResult.Outcome.FAILED);
    }

    private static DefaultResponseCreator page(String body, String etag, String expires, String lastModified) {
        return withSuccess(body, MediaType.APPLICATION_JSON)
                .header("ETag", etag).header("Expires", expires).header("Last-Modified", lastModified);
    }
}
