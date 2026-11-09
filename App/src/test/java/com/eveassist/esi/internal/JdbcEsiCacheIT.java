package com.eveassist.esi.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.eveassist.IntegrationTest;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class JdbcEsiCacheIT extends IntegrationTest {

    @Autowired
    EsiCache cache;

    @Test
    void remembersWhatEsiSaidAndReplacesItOnTheNextFetch() {
        var key = "test/" + newSub();
        assertThat(cache.find(key)).isEmpty();

        var first = new EsiCache.Entry("\"one\"", Instant.parse("2026-11-08T21:00:00Z"), Instant.parse("2026-11-08T21:05:00Z"));
        cache.save(key, first, Instant.parse("2026-11-08T21:00:01Z"));
        assertThat(cache.find(key)).contains(first);

        var second = new EsiCache.Entry(null, null, Instant.parse("2026-11-08T21:10:00Z"));
        cache.save(key, second, Instant.parse("2026-11-08T21:05:01Z"));
        assertThat(cache.find(key)).contains(second);
    }
}
