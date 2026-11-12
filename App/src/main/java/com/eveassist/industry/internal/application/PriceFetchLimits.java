package com.eveassist.industry.internal.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * How many prices a visitor without an account can have fetched from ESI. ESI gives the whole app one allowance for
 * market orders, so one visitor is limited, and all visitors together are limited to a part of it, which leaves the rest
 * for people who are signed in. A price that is already stored costs nothing and never comes here. The numbers are
 * starting values (see the requirements) and are to be tuned against real use.
 */
@Component
class PriceFetchLimits {

    static final int PER_VISITOR = 200;
    static final Duration VISITOR_WINDOW = Duration.ofHours(1);
    static final int ALL_VISITORS = 1_500;
    static final Duration ALL_WINDOW = Duration.ofMinutes(15);
    /** Visitors remembered at most, so that a flood of addresses cannot fill the memory. */
    private static final int MOST_VISITORS = 10_000;

    private record Use(Instant at, int items) {
    }

    private final Clock clock;
    private final Map<String, Deque<Use>> visitors = new HashMap<>();
    private final Deque<Use> everyone = new ArrayDeque<>();

    PriceFetchLimits(Clock clock) {
        this.clock = clock;
    }

    /** Takes up to the wanted number of fetches for this visitor and says how many were allowed. */
    synchronized int take(String visitor, int wanted) {
        var now = clock.instant();
        var mine = visitors.computeIfAbsent(visitor, key -> new ArrayDeque<>());
        int mineUsed = used(mine, now.minus(VISITOR_WINDOW));
        int allUsed = used(everyone, now.minus(ALL_WINDOW));
        int allowed = Math.max(0, Math.min(wanted, Math.min(PER_VISITOR - mineUsed, ALL_VISITORS - allUsed)));
        if (allowed > 0) {
            mine.addLast(new Use(now, allowed));
            everyone.addLast(new Use(now, allowed));
        }
        forgetQuietVisitors(now);
        return allowed;
    }

    private static int used(Deque<Use> uses, Instant since) {
        while (!uses.isEmpty() && uses.peekFirst().at().isBefore(since)) {
            uses.removeFirst();
        }
        return uses.stream().mapToInt(Use::items).sum();
    }

    private void forgetQuietVisitors(Instant now) {
        if (visitors.size() > MOST_VISITORS) {
            var since = now.minus(VISITOR_WINDOW);
            visitors.values().removeIf(uses -> used(uses, since) == 0);
        }
    }
}
