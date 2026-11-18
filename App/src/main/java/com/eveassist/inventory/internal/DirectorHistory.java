package com.eveassist.inventory.internal;

import com.eveassist.characters.LinkedCharacter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Which Director of a corporation to ask first. A corporation is read through one Director, and the next is tried only
 * when that one fails, so a Director who lost the role (ESI answers 403, which costs 5 tokens and counts towards the
 * error limit) would be tried first at every run. This remembers, in memory, the Director who last worked and the ones
 * that failed while another worked. Those are left out for a while and then given one more try. Nothing is stored: after
 * a restart the first run asks in list order again.
 *
 * <p>A failure is only remembered when another Director then worked. When every Director fails, the cause is more
 * likely ESI than any one of them, so nobody is avoided.
 */
@Component
class DirectorHistory {

    /** How long a Director who failed while another worked is left out. */
    static final Duration AVOID_FAILED_FOR = Duration.ofMinutes(15);

    private final Clock clock;
    private final Map<String, Long> lastWorked = new ConcurrentHashMap<>();
    private final Map<String, Instant> failedAt = new ConcurrentHashMap<>();

    DirectorHistory(Clock clock) {
        this.clock = clock;
    }

    /** The Directors worth asking, the one who last worked first and then in the order given. */
    List<LinkedCharacter> order(UUID corporationId, String route, List<LinkedCharacter> directors) {
        var now = clock.instant();
        var good = lastWorked.get(corporationId + "/" + route);
        return directors.stream().filter(director -> !avoided(corporationId, route, director, now))
                .sorted(Comparator.comparingInt(director -> Objects.equals(good, director.eveCharacterId()) ? 0 : 1))
                .toList();
    }

    /** {@code director} worked, so those {@code failedBefore} it in this run are at fault, not ESI. */
    void worked(UUID corporationId, String route, LinkedCharacter director, List<LinkedCharacter> failedBefore) {
        var now = clock.instant();
        lastWorked.put(corporationId + "/" + route, director.eveCharacterId());
        failedAt.remove(failureKey(corporationId, route, director));
        failedBefore.forEach(failed -> failedAt.put(failureKey(corporationId, route, failed), now));
    }

    /** Forgets who failed, for a run the viewer asked for with a button: something may have been put right. */
    void forgetFailures(UUID corporationId, String route) {
        var prefix = corporationId + "/" + route + "/";
        failedAt.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private boolean avoided(UUID corporationId, String route, LinkedCharacter director, Instant now) {
        var failed = failedAt.get(failureKey(corporationId, route, director));
        return failed != null && failed.plus(AVOID_FAILED_FOR).isAfter(now);
    }

    private static String failureKey(UUID corporationId, String route, LinkedCharacter director) {
        return corporationId + "/" + route + "/" + director.eveCharacterId();
    }
}
