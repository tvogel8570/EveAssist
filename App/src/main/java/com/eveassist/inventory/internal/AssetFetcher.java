package com.eveassist.inventory.internal;

import com.eveassist.characters.LinkedCharacter;
import com.eveassist.characters.LinkedCharacters;
import com.eveassist.esi.EsiClient;
import com.eveassist.esi.EsiResult;
import com.eveassist.identity.Viewer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Fetches the assets of a viewer's characters in the background (see esi.md, Background fetches). A task is one
 * character and one kind of data, and runs on its own virtual thread. A character already being fetched is skipped, so
 * two requests never overlap on it. The ESI client's cache rule decides whether ESI is asked at all, so opening the
 * page again costs nothing while ESI's copy is current. Nothing is fetched for a signed-out user: the tasks start from a
 * request and are not remembered over a restart.
 */
@Service
public class AssetFetcher {

    private static final Logger log = LoggerFactory.getLogger(AssetFetcher.class);

    /** What the page says about one character's assets. */
    public enum Phase {
        /** Never fetched, and not being fetched now. */
        NEVER,
        /** Being fetched now. */
        RUNNING,
        /** Fetched, and the last attempt worked. */
        CURRENT,
        /** The last attempt failed. Stored data, if any, is still shown with its age. */
        FAILED,
        /** EVE refused the saved access. Only linking the character again helps. */
        NEEDS_REAUTHORIZATION,
        /** The character did not grant the scope that assets need. */
        MISSING_SCOPE
    }

    /**
     * @param fetchedAt when ESI last answered, with new data or "unchanged"
     * @param itemCount how many item rows are stored for the character
     * @param problem   a short reason when the last attempt failed
     * @param structures      how many player structures the character has items in
     * @param structuresNamed how many of those have a name, which only a character that may dock there can learn
     */
    public record CharacterAssets(UUID characterId, String name, Phase phase, Instant fetchedAt, Integer itemCount,
            String problem, int structures, int structuresNamed) {
    }

    private final LinkedCharacters linked;
    private final EsiClient esi;
    private final AssetRepository assets;
    private final Clock clock;
    private final StructureResolver resolver;
    private final StructureRepository structures;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Set<UUID> running = ConcurrentHashMap.newKeySet();
    /**
     * Storing a character's assets is one transaction that holds a pooled connection while thousands of rows are
     * replaced. With 20 characters all fetched at once, those transactions used up the whole pool of 10 for 30 s and
     * the page itself could not get a connection (seen 2026-11-10). So only this many characters are fetched at a time
     * and the rest wait their turn, which leaves connections for requests. See
     * documentation/runbooks/concurrency-tuning.md.
     */
    static final int MOST_CONCURRENT_FETCHES = 3;
    private final Semaphore turns = new Semaphore(MOST_CONCURRENT_FETCHES);
    /** From queued to done for one character: the wait for a turn and the fetch. It grows when users queue up. */
    private final Timer turnaround;

    AssetFetcher(LinkedCharacters linked, EsiClient esi, AssetRepository assets, Clock clock,
            StructureResolver resolver, StructureRepository structures, MeterRegistry meters) {
        this.turnaround = Timer.builder("eveassist.asset.fetch.turnaround")
                .description("Time from a character's assets being queued to the fetch being done").register(meters);
        Gauge.builder("eveassist.asset.fetch.waiting", turns, Semaphore::getQueueLength)
                .description("Characters whose assets wait for one of the fetch turns").register(meters);
        this.resolver = resolver;
        this.structures = structures;
        this.linked = linked;
        this.esi = esi;
        this.assets = assets;
        this.clock = clock;
    }

    /**
     * Queues a fetch for each of the viewer's characters that can be fetched, and returns at once. A character that
     * needs re-authorization or lacks the scope is not asked about: ESI would answer 403, which counts as an error.
     */
    public void refresh(Viewer viewer) {
        for (var character : linked.of(viewer)) {
            if (canFetch(character) && running.add(character.id())) {
                var queued = Timer.start();
                executor.execute(() -> {
                    try {
                        turns.acquireUninterruptibly();
                        try {
                            fetch(character);
                        } finally {
                            turns.release();
                        }
                    } finally {
                        running.remove(character.id());
                        queued.stop(turnaround);
                    }
                });
            }
        }
    }

    /** Fetches one character now, on the calling thread. Used by the background task and by tests. */
    void fetch(LinkedCharacter character) {
        var now = clock.instant();
        EsiResult<List<com.eveassist.esi.CharacterAsset>> result;
        try {
            result = esi.characterAssets(character.eveCharacterId());
        } catch (RuntimeException e) {
            log.warn("Fetching the assets of a character failed: {}", e.getClass().getSimpleName());
            try {
                assets.recordFailure(character.id(), now, "The fetch failed unexpectedly");
            } catch (DataIntegrityViolationException gone) {
                // the character was removed while ESI was being asked, and there is nothing left to record on
                log.debug("A character was removed during its asset fetch");
            }
            return;
        }
        try {
            switch (result.outcome()) {
                case UPDATED -> assets.replace(character.id(), result.data(), now);
                case UNCHANGED -> assets.recordUnchanged(character.id(), now);
                case FAILED -> assets.recordFailure(character.id(), now, result.problem());
            }
            if (result.outcome() != EsiResult.Outcome.FAILED) {
                // after the rows are stored, and also when ESI said nothing changed, to finish what an earlier run left
                resolver.resolve(character);
            }
        } catch (DataIntegrityViolationException gone) {
            // the character was removed while ESI was being asked, and its rows went with it
            log.debug("A character was removed during its asset fetch");
        }
    }

    /** Where each of the viewer's characters stands, for the page. */
    public List<CharacterAssets> status(Viewer viewer) {
        var characters = linked.of(viewer);
        Map<UUID, AssetRepository.FetchState> states = assets.state(characters.stream().map(LinkedCharacter::id).toList())
                .stream().collect(Collectors.toMap(AssetRepository.FetchState::characterId, Function.identity()));
        return characters.stream().map(character -> {
            var state = states.get(character.id());
            var phase = phaseOf(character, state);
            var counts = structures.counts(character.id());
            return new CharacterAssets(character.id(), character.name(), phase,
                    state == null ? null : state.fetchedAt(), state == null ? null : state.itemCount(),
                    state == null ? null : state.problem(), counts.total(), counts.named());
        }).toList();
    }

    /** True while any of the viewer's characters is being fetched, so the page keeps asking for news. */
    public boolean anyRunning(Viewer viewer) {
        return linked.of(viewer).stream().anyMatch(character -> running.contains(character.id()));
    }

    private Phase phaseOf(LinkedCharacter character, AssetRepository.FetchState state) {
        if (running.contains(character.id())) {
            return Phase.RUNNING;
        }
        if (character.needsReauthorization()) {
            return Phase.NEEDS_REAUTHORIZATION;
        }
        if (!character.scopes().contains(EsiClient.ASSETS_SCOPE)) {
            return Phase.MISSING_SCOPE;
        }
        if (state == null) {
            return Phase.NEVER;
        }
        return state.problem() == null ? Phase.CURRENT : Phase.FAILED;
    }

    private static boolean canFetch(LinkedCharacter character) {
        return !character.needsReauthorization() && character.scopes().contains(EsiClient.ASSETS_SCOPE);
    }

    @PreDestroy
    void stop() {
        executor.shutdownNow();
    }
}
