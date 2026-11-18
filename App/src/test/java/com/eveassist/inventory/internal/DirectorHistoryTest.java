package com.eveassist.inventory.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.eveassist.characters.LinkedCharacter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Which Director of a corporation is asked first, and when one that failed is asked again. */
class DirectorHistoryTest {

    private static final String ROUTE = "blueprints";

    private Instant now = Instant.parse("2026-11-18T09:00:00Z");
    private final Clock clock = new Clock() {
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
    };
    private final DirectorHistory history = new DirectorHistory(clock);
    private final UUID corporation = UUID.randomUUID();
    private final LinkedCharacter first = director(1001);
    private final LinkedCharacter second = director(1002);
    private final LinkedCharacter third = director(1003);

    private static LinkedCharacter director(long eveId) {
        return new LinkedCharacter(UUID.randomUUID(), eveId, "Director " + eveId, Set.of(), false);
    }

    @Test
    void withNoHistoryTheDirectorsAreAskedInTheOrderGiven() {
        assertThat(history.order(corporation, ROUTE, List.of(first, second, third))).containsExactly(first, second, third);
    }

    @Test
    void theDirectorWhoLastWorkedIsAskedFirst() {
        history.worked(corporation, ROUTE, second, List.of());

        assertThat(history.order(corporation, ROUTE, List.of(first, second, third))).containsExactly(second, first, third);
    }

    @Test
    void aDirectorWhoFailedWhileAnotherWorkedIsLeftOutForAWhileAndThenAskedAgain() {
        history.worked(corporation, ROUTE, second, List.of(first));

        assertThat(history.order(corporation, ROUTE, List.of(first, second))).containsExactly(second);
        now = now.plus(DirectorHistory.AVOID_FAILED_FOR).minusSeconds(1);
        assertThat(history.order(corporation, ROUTE, List.of(first, second))).containsExactly(second);
        now = now.plusSeconds(2);
        assertThat(history.order(corporation, ROUTE, List.of(first, second))).containsExactly(second, first);
    }

    @Test
    void whenEveryDirectorHasFailedNoneIsAskedUntilTheyAreForgotten() {
        history.worked(corporation, ROUTE, third, List.of(first, second));
        // third worked, then lost the role: it is not remembered as failed, so it is still asked, first
        assertThat(history.order(corporation, ROUTE, List.of(first, second, third))).containsExactly(third);

        history.worked(corporation, ROUTE, first, List.of(third));
        assertThat(history.order(corporation, ROUTE, List.of(first, second, third))).containsExactly(first);
        history.forgetFailures(corporation, ROUTE);
        assertThat(history.order(corporation, ROUTE, List.of(first, second, third))).containsExactly(first, second, third);
    }

    @Test
    void theHistoryIsKeptForEachCorporationAndRoute() {
        history.worked(corporation, ROUTE, second, List.of(first));

        assertThat(history.order(corporation, "assets", List.of(first, second))).containsExactly(first, second);
        assertThat(history.order(UUID.randomUUID(), ROUTE, List.of(first, second))).containsExactly(first, second);
        assertThat(Duration.ofMinutes(15)).isEqualTo(DirectorHistory.AVOID_FAILED_FOR);
    }
}
