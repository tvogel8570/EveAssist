package com.eveassist.esi.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongConsumer;
import org.springframework.http.HttpHeaders;

/**
 * Keeps EveAssist inside ESI's limits (see ESI's rate-limiting and best-practices pages): a bucket per rate-limit
 * group that is slowed down well before it is empty and blocked after a 429, and the error limit that pauses every call
 * when few errors are left.
 */
class EsiLimiter {

    /** A call that would have to wait longer than this fails instead, so a page never hangs on a throttled ESI. */
    static final Duration LONGEST_WAIT = Duration.ofSeconds(10);
    /** Below this share of a bucket, calls are spread out. A first guess, to be tuned against real responses. */
    private static final double SLOW_DOWN_BELOW = 0.10;
    private static final Duration SLOW_DOWN_PAUSE = Duration.ofSeconds(1);
    private static final int ERROR_LIMIT_FLOOR = 20;

    private final Clock clock;
    private final LongConsumer sleeper;
    private final Map<String, Instant> blockedUntil = new ConcurrentHashMap<>();
    private final Map<String, Boolean> nearlyEmpty = new ConcurrentHashMap<>();
    private volatile Instant errorLimitPausedUntil = Instant.MIN;

    EsiLimiter(Clock clock, LongConsumer sleeper) {
        this.clock = clock;
        this.sleeper = sleeper;
    }

    /** Waits when the group or the error limit asks for it. Returns why the call must not be made, if it must not. */
    Optional<String> before(String group) {
        var now = clock.instant();
        var until = blockedUntil.getOrDefault(group, Instant.MIN);
        if (errorLimitPausedUntil.isAfter(until)) {
            until = errorLimitPausedUntil;
        }
        var wait = until.isAfter(now) ? Duration.between(now, until) : Duration.ZERO;
        if (wait.compareTo(LONGEST_WAIT) > 0) {
            return Optional.of("ESI is throttling " + group + " for another " + wait.toSeconds() + " s");
        }
        if (wait.isZero() && nearlyEmpty.getOrDefault(group, false)) {
            wait = SLOW_DOWN_PAUSE;
        }
        if (!wait.isZero()) {
            sleeper.accept(wait.toMillis());
        }
        return Optional.empty();
    }

    void record(String group, int status, HttpHeaders headers) {
        var now = clock.instant();
        var limit = headers.getFirst("X-Ratelimit-Limit");
        var remaining = headers.getFirst("X-Ratelimit-Remaining");
        if (limit != null && remaining != null) {
            nearlyEmpty.put(group, remainingShare(limit, remaining) < SLOW_DOWN_BELOW);
        }
        if (status == 429) {
            var retryAfter = number(headers.getFirst("Retry-After"), 60);
            blockedUntil.put(group, now.plusSeconds(retryAfter));
        }
        var errorsLeft = headers.getFirst("X-ESI-Error-Limit-Remain");
        if (errorsLeft != null && number(errorsLeft, Long.MAX_VALUE) < ERROR_LIMIT_FLOOR) {
            errorLimitPausedUntil = now.plusSeconds(number(headers.getFirst("X-ESI-Error-Limit-Reset"), 60));
        }
    }

    private static double remainingShare(String limit, String remaining) {
        var total = number(limit.split("/")[0], 0);
        return total == 0 ? 1.0 : (double) number(remaining, total) / total;
    }

    private static long number(String text, long fallback) {
        try {
            return Long.parseLong(text.trim());
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
