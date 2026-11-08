package com.eveassist.esi.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class EsiLimiterTest {

    private final TestClock clock = new TestClock(Instant.parse("2026-11-08T21:00:00Z"));
    private final List<Long> slept = new ArrayList<>();
    private final EsiLimiter limiter = new EsiLimiter(clock, slept::add);

    @Test
    void letsCallsThroughWhenNothingIsWrong() {
        assertThat(limiter.before("market-order")).isEmpty();
        assertThat(slept).isEmpty();
    }

    @Test
    void spreadsCallsOutWhenABucketIsNearlyEmpty() {
        limiter.record("market-order", 200, headers("X-Ratelimit-Limit", "12000/15m", "X-Ratelimit-Remaining", "900"));

        assertThat(limiter.before("market-order")).isEmpty();
        assertThat(slept).containsExactly(1000L);
        assertThat(limiter.before("char-wallet")).isEmpty();
        assertThat(slept).hasSize(1);
    }

    @Test
    void doesNotSlowDownWhileTheBucketIsHealthy() {
        limiter.record("market-order", 200, headers("X-Ratelimit-Limit", "12000/15m", "X-Ratelimit-Remaining", "11998"));

        assertThat(limiter.before("market-order")).isEmpty();
        assertThat(slept).isEmpty();
    }

    @Test
    void refusesACallInsteadOfWaitingLongAfterA429() {
        limiter.record("market-order", 429, headers("Retry-After", "30"));

        assertThat(limiter.before("market-order")).hasValueSatisfying(
                problem -> assertThat(problem).contains("throttling market-order"));
        assertThat(limiter.before("char-wallet")).isEmpty();
    }

    @Test
    void waitsForAShortRetryAfterAndThenCarriesOn() {
        limiter.record("market-order", 429, headers("Retry-After", "4"));

        assertThat(limiter.before("market-order")).isEmpty();
        assertThat(slept).containsExactly(4000L);

        clock.advance(Duration.ofSeconds(5));
        assertThat(limiter.before("market-order")).isEmpty();
        assertThat(slept).hasSize(1);
    }

    @Test
    void pausesEveryGroupWhenFewErrorsAreLeft() {
        limiter.record("market-order", 404, headers("X-ESI-Error-Limit-Remain", "5", "X-ESI-Error-Limit-Reset", "45"));

        assertThat(limiter.before("char-wallet")).isPresent();

        clock.advance(Duration.ofSeconds(46));
        assertThat(limiter.before("char-wallet")).isEmpty();
    }

    private static HttpHeaders headers(String... namesAndValues) {
        var headers = new HttpHeaders();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            headers.add(namesAndValues[i], namesAndValues[i + 1]);
        }
        return headers;
    }
}
