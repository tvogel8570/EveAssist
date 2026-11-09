package com.eveassist.inventory.internal;

import com.eveassist.characters.LinkedCharacter;
import com.eveassist.characters.LinkedCharacters;
import com.eveassist.esi.EsiClient;
import com.eveassist.esi.EsiResult;
import com.eveassist.identity.Viewer;
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
     */
    public record CharacterAssets(UUID characterId, String name, Phase phase, Instant fetchedAt, Integer itemCount,
            String problem) {
    }

    private final LinkedCharacters linked;
    private final EsiClient esi;
    private final AssetRepository assets;
    private final Clock clock;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Set<UUID> running = ConcurrentHashMap.newKeySet();

    AssetFetcher(LinkedCharacters linked, EsiClient esi, AssetRepository assets, Clock clock) {
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
                executor.execute(() -> {
                    try {
                        fetch(character);
                    } finally {
                        running.remove(character.id());
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
            assets.recordFailure(character.id(), now, "The fetch failed unexpectedly");
            return;
        }
        try {
            switch (result.outcome()) {
                case UPDATED -> assets.replace(character.id(), result.data(), now);
                case UNCHANGED -> assets.recordUnchanged(character.id(), now);
                case FAILED -> assets.recordFailure(character.id(), now, result.problem());
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
            return new CharacterAssets(character.id(), character.name(), phase,
                    state == null ? null : state.fetchedAt(), state == null ? null : state.itemCount(),
                    state == null ? null : state.problem());
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
