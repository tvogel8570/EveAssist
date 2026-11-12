package com.eveassist.industry.internal.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class PriceFetchLimitsTest {

    private static final class Moving extends Clock {

        Instant now = Instant.parse("2026-11-10T12:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final Moving clock = new Moving();
    private final PriceFetchLimits limits = new PriceFetchLimits(clock);

    @Test
    void oneVisitorGetsTwoHundredAnHourAndNoMore() {
        assertThat(limits.take("a", 150)).isEqualTo(150);
        assertThat(limits.take("a", 150)).isEqualTo(50);
        assertThat(limits.take("a", 1)).isZero();
    }

    @Test
    void theAllowanceComesBackAfterAnHour() {
        limits.take("a", 200);
        clock.now = clock.now.plus(Duration.ofMinutes(61));

        assertThat(limits.take("a", 10)).isEqualTo(10);
    }

    @Test
    void anotherVisitorIsNotAffected() {
        limits.take("a", 200);

        assertThat(limits.take("b", 10)).isEqualTo(10);
    }

    @Test
    void allVisitorsTogetherGetFifteenHundredEveryFifteenMinutes() {
        for (int i = 0; i < 7; i++) {
            assertThat(limits.take("v" + i, 200)).isEqualTo(200);
        }
        assertThat(limits.take("v7", 200)).isEqualTo(100);
        assertThat(limits.take("v8", 1)).isZero();

        clock.now = clock.now.plus(Duration.ofMinutes(16));

        assertThat(limits.take("v9", 50)).isEqualTo(50);
    }

    @Test
    void askingForNothingTakesNothing() {
        assertThat(limits.take("a", 0)).isZero();
        assertThat(limits.take("a", 200)).isEqualTo(200);
    }
}
