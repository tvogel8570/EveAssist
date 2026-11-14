package com.eveassist.esi.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.eveassist.esi.EsiResult;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.http.client.autoconfigure.HttpClientAutoConfiguration;
import org.springframework.boot.http.client.autoconfigure.imperative.ImperativeHttpClientAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

/** A server that stalls must make a call fail, not leave it waiting for ever. */
class EsiTimeoutTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(HttpClientAutoConfiguration.class,
                    ImperativeHttpClientAutoConfiguration.class, RestClientAutoConfiguration.class));

    private final EsiCache noCache = new EsiCache() {
        @Override
        public Optional<Entry> find(String key) {
            return Optional.empty();
        }

        @Override
        public void save(String key, Entry entry, Instant fetchedAt) {
        }

        @Override
        public void forgetPrefix(String keyPrefix) {
        }
    };

    @Test
    void theApplicationSetsAConnectAndAReadTimeout() {
        runner.run(context -> {
            var environment = context.getEnvironment();
            assertThat(environment.getProperty("spring.http.clients.connect-timeout")).isEqualTo("5s");
            assertThat(environment.getProperty("spring.http.clients.read-timeout")).isEqualTo("30s");
        });
    }

    @Test
    void aServerThatNeverAnswersMakesTheCallFail() throws Exception {
        try (var server = new ServerSocket(0)) {
            var result = callWithReadTimeout(server);

            assertThat(result.outcome()).isEqualTo(EsiResult.Outcome.FAILED);
        }
    }

    @Test
    void aServerThatStallsAfterTheHeadersMakesTheCallFail() throws Exception {
        try (var server = new ServerSocket(0)) {
            var staller = Thread.ofVirtual().start(() -> {
                try (var socket = server.accept()) {
                    socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n"
                            + "Content-Length: 1000\r\n\r\n[").getBytes(StandardCharsets.UTF_8));
                    socket.getOutputStream().flush();
                    Thread.sleep(20_000);
                } catch (IOException | InterruptedException e) {
                    // the client gave up, which is what the test wants
                }
            });
            try {
                var result = callWithReadTimeout(server);

                assertThat(result.outcome()).isEqualTo(EsiResult.Outcome.FAILED);
            } finally {
                staller.interrupt();
            }
        }
    }

    private EsiResult<?> callWithReadTimeout(ServerSocket server) {
        var result = new EsiResult<?>[1];
        var started = Instant.now();
        runner.withPropertyValues("spring.http.clients.read-timeout=500ms").run(context -> {
            var properties = new EsiProperties("http://localhost:" + server.getLocalPort(), "", "2026-08-18", 8);
            var clock = new TestClock(Instant.parse("2026-11-09T12:00:00Z"));
            var client = new HttpEsiClient(context.getBean(RestClient.Builder.class), properties, noCache,
                    new EsiLimiter(clock, millis -> { }), clock);
            result[0] = client.marketOrders(10000002, 34);
        });
        assertThat(Duration.between(started, Instant.now())).isLessThan(Duration.ofSeconds(10));
        return result[0];
    }
}
