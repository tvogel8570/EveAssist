package com.eveassist.esi.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.eveassist.esi.EsiResult;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/** The cap on simultaneous calls is EveAssist's own rule, so it is checked against a real server, not a mock. */
class HttpEsiClientConcurrencyTest {

    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger mostInFlight = new AtomicInteger();
    private HttpServer server;

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void neverMakesMoreCallsAtOnceThanTheConfiguredLimit() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", exchange -> {
            mostInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            inFlight.decrementAndGet();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, 2);
            exchange.getResponseBody().write("[]".getBytes());
            exchange.close();
        });
        server.start();
        var cache = new HashMap<String, EsiCache.Entry>();
        var properties = new EsiProperties("http://127.0.0.1:" + server.getAddress().getPort(), "", "2026-08-18", 8);
        var client = new HttpEsiClient(RestClient.builder(), properties, new EsiCache() {
            @Override
            public synchronized Optional<Entry> find(String key) {
                return Optional.ofNullable(cache.get(key));
            }

            @Override
            public synchronized void save(String key, Entry entry, Instant fetchedAt) {
                cache.put(key, entry);
            }

            @Override
            public synchronized void forgetPrefix(String keyPrefix) {
                cache.keySet().removeIf(key -> key.startsWith(keyPrefix));
            }
        }, new EsiLimiter(Clock.systemUTC(), millis -> { }), Clock.systemUTC());

        var results = new ArrayList<java.util.concurrent.Future<EsiResult<?>>>();
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int type = 1; type <= 40; type++) {
                int typeId = type;
                results.add(threads.submit(() -> client.marketOrders(10000002, typeId)));
            }
        }

        for (var result : results) {
            assertThat(result.get().outcome()).isEqualTo(EsiResult.Outcome.UPDATED);
        }
        assertThat(mostInFlight.get()).isEqualTo(8);
    }
}
