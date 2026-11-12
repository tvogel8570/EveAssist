package com.eveassist.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.eveassist.IntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The timing of outgoing HTTP calls is switched off, because ESI URLs carry ids and every call became a new {@code uri}
 * tag value (see application.yaml). A meter with that name must be refused, and other meters must still work.
 */
class ClientMetricsOffIT extends IntegrationTest {

    @Autowired
    MeterRegistry meters;

    @Test
    void aMeterNamedAsTheClientRequestTimerIsRefusedAndOthersAreKept() {
        Timer.builder("http.client.requests").tag("uri", "/characters/1/assets").register(meters)
                .record(java.time.Duration.ofMillis(5));
        Timer.builder("eveassist.some.other.timer").register(meters).record(java.time.Duration.ofMillis(5));

        assertThat(meters.find("http.client.requests").timers()).isEmpty();
        assertThat(meters.find("eveassist.some.other.timer").timer().count()).isEqualTo(1);
        assertThat(meters.find("hikaricp.connections.max").gauge()).isNotNull();
    }
}
