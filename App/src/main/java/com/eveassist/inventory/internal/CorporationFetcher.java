package com.eveassist.inventory.internal;

import com.eveassist.characters.Feature;
import com.eveassist.characters.LinkedCharacter;
import com.eveassist.characters.LinkedCharacters;
import com.eveassist.esi.EsiClient;
import com.eveassist.esi.EsiResult;
import com.eveassist.identity.Viewer;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
 * Reads the assets of the corporations an account's own characters belong to, in the background, and only while the
 * account has the Corporation assets feature on (see esi.md, Corporation data). ESI gives a corporation's assets only
 * to a character with the Director role, so a run first learns which corporation each linked character is in and
 * whether it is a Director, and then reads each corporation with the token of one of its Directors. One run is one
 * account, on one virtual thread, and a second request for an account that is running is skipped. ESI's cache times
 * decide whether ESI is asked about the assets at all. Nothing is fetched for a signed-out user.
 */
@Service
public class CorporationFetcher {

    private static final Logger log = LoggerFactory.getLogger(CorporationFetcher.class);

    /** The corporation's name and the names of its hangars change rarely. ESI caches both for an hour. */
    static final Duration NAME_TRUSTED_FOR = Duration.ofDays(1);
    static final Duration DIVISIONS_TRUSTED_FOR = Duration.ofHours(1);

    /** What the page says about one corporation's assets. */
    public enum Phase {
        /** Not fetched yet, and not being fetched now. */
        NEVER,
        /** Being looked at now. */
        RUNNING,
        /** Fetched, and the last attempt worked. */
        CURRENT,
        /** The last attempt failed. Stored data, if any, is still shown with its age. */
        FAILED,
        /** None of the account's linked characters in the corporation is a Director, so ESI will not say. */
        NO_DIRECTOR,
        /** A Director is linked, but it needs authorization or lacks a permission, so it cannot be used. */
        NEEDS_AUTHORIZATION
    }

    /**
     * @param via       the Director whose token reads it, or null when none can
     * @param directors how many of the account's characters in it are Directors
     */
    public record CorporationAssets(UUID corporationId, String name, Phase phase, Instant fetchedAt,
            Integer itemCount, String problem, String via, int directors) {
    }

    private final LinkedCharacters linked;
    private final EsiClient esi;
    private final CorporationRepository corporations;
    private final StructureResolver resolver;
    private final Clock clock;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Set<UUID> running = ConcurrentHashMap.newKeySet();

    CorporationFetcher(LinkedCharacters linked, EsiClient esi, CorporationRepository corporations,
            StructureResolver resolver, Clock clock) {
        this.linked = linked;
        this.esi = esi;
        this.corporations = corporations;
        this.resolver = resolver;
        this.clock = clock;
    }

    /** True when the viewer's account has turned the feature on. Without it nothing is asked of ESI or shown. */
    public boolean enabled(Viewer viewer) {
        return linked.featuresOn(viewer).contains(Feature.CORPORATION_ASSETS);
    }

    /** Queues a run for the viewer's account and returns at once. */
    public void refresh(Viewer viewer) {
        refresh(viewer, false);
    }

    /** As above. With {@code rereadNames} container names are asked for again even when the asset list is unchanged. */
    public void refresh(Viewer viewer, boolean rereadNames) {
        if (!enabled(viewer)) {
            return;
        }
        var account = viewer.accountId().value();
        if (running.add(account)) {
            executor.execute(() -> {
                try {
                    run(viewer, rereadNames);
                } catch (RuntimeException e) {
                    log.warn("Reading the corporation assets of an account failed: {}", e.getClass().getSimpleName());
                } finally {
                    running.remove(account);
                }
            });
        }
    }

    /** One run on the calling thread. Used by the background task and by tests. */
    void run(Viewer viewer) {
        run(viewer, false);
    }

    void run(Viewer viewer, boolean rereadNames) {
        var account = viewer.accountId().value();
        // which corporation each character is in, and who is a Director, as the characters module reads it from ESI
        linked.learnCorporations(viewer);
        var characters = linked.of(viewer);
        // the corporations of the account are the ones its characters are in, as last learned
        var members = new LinkedHashMap<Long, List<LinkedCharacter>>();
        characters.stream().filter(c -> c.eveCorporationId() != null)
                .forEach(c -> members.computeIfAbsent(c.eveCorporationId(), id -> new ArrayList<>()).add(c));
        corporations.dropAbandoned(account, members.keySet());
        for (var entry : members.entrySet()) {
            var corporation = learnCorporation(account, entry.getKey());
            var directors = entry.getValue().stream().filter(c -> Boolean.TRUE.equals(c.director()) && canRead(c))
                    .toList();
            if (!directors.isEmpty()) {
                fetch(corporation, directors, rereadNames);
            }
        }
    }

    /** The account's corporation, created when first seen and named again after a day. */
    private CorporationRepository.Corporation learnCorporation(UUID account, long eveCorporationId) {
        var now = clock.instant();
        var known = corporations.find(account, eveCorporationId);
        if (known.isPresent() && known.get().namedAt().plus(NAME_TRUSTED_FOR).isAfter(now)) {
            return known.get();
        }
        var name = esi.corporationName(eveCorporationId);
        if (name.outcome() == EsiResult.Outcome.UPDATED && name.data() != null && !name.data().isBlank()) {
            corporations.saveCorporation(account, eveCorporationId, name.data(), now);
        } else if (known.isEmpty()) {
            // a placeholder until ESI answers, asked about again at the next run because its name time is the epoch
            corporations.saveCorporation(account, eveCorporationId, "Corporation " + eveCorporationId, Instant.EPOCH);
        }
        return corporations.find(account, eveCorporationId).orElseThrow();
    }

    /** Reads the hangar names and the assets with the first Director that gets an answer. */
    private void fetch(CorporationRepository.Corporation corporation, List<LinkedCharacter> directors,
            boolean rereadNames) {
        for (var director : directors) {
            var now = clock.instant();
            EsiResult<List<com.eveassist.esi.CharacterAsset>> result;
            try {
                if (corporation.divisionsAt() == null
                        || corporation.divisionsAt().plus(DIVISIONS_TRUSTED_FOR).isBefore(now)) {
                    var names = esi.corporationHangars(corporation.eveCorporationId(), director.eveCharacterId());
                    if (names.outcome() == EsiResult.Outcome.UPDATED) {
                        corporations.replaceDivisions(corporation.id(), names.data(), now);
                    } else {
                        corporations.touchDivisions(corporation.id(), now);
                    }
                }
                result = esi.corporationAssets(corporation.eveCorporationId(), director.eveCharacterId());
            } catch (RuntimeException e) {
                log.warn("Fetching the assets of a corporation failed: {}", e.getClass().getSimpleName());
                recordFailure(corporation.id(), now, "The fetch failed unexpectedly");
                return;
            }
            try {
                switch (result.outcome()) {
                    case UPDATED -> corporations.replace(corporation.id(), result.data(),
                            namesOf(corporation, director, result.data()), now);
                    case UNCHANGED -> {
                        corporations.recordUnchanged(corporation.id(), now);
                        if (rereadNames) {
                            rereadNames(corporation, director);
                        }
                    }
                    case FAILED -> corporations.recordFailure(corporation.id(), now, result.problem());
                }
                if (result.outcome() != EsiResult.Outcome.FAILED) {
                    resolver.resolveForCorporation(corporation.id(), director);
                    return;
                }
            } catch (DataIntegrityViolationException gone) {
                log.debug("A corporation was removed during its asset fetch");
                return;
            }
            // this Director failed (it may have lost the role): the next one gets its turn
        }
    }

    /** Asks again for the names of the containers already stored and updates them in place. A failed call keeps them. */
    private void rereadNames(CorporationRepository.Corporation corporation, LinkedCharacter director) {
        var holders = corporations.holderItemIds(corporation.id());
        if (holders.isEmpty()) {
            return;
        }
        try {
            var names = esi.corporationAssetNames(corporation.eveCorporationId(), director.eveCharacterId(), holders);
            if (names.outcome() == EsiResult.Outcome.UPDATED) {
                corporations.updateNames(corporation.id(), holders, names.data());
                log.info("Container names of corporation {} read again: asked for {}, {} have a name",
                        corporation.eveCorporationId(), holders.size(), names.data().size());
            } else {
                log.warn("Names of the containers of corporation {} could not be read again (asked for {}): {}",
                        corporation.eveCorporationId(), holders.size(), names.problem());
            }
        } catch (RuntimeException e) {
            log.warn("Asking again for the names of a corporation's containers failed: {}", e.getClass().getSimpleName());
        }
    }

    /** The names players gave to the corporation's containers and ships, asked with the Director's token. */
    private java.util.Map<Long, String> namesOf(CorporationRepository.Corporation corporation, LinkedCharacter director,
            List<com.eveassist.esi.CharacterAsset> list) {
        var candidates = AssetClassifier.namedCandidates(list);
        if (candidates.isEmpty()) {
            return java.util.Map.of();
        }
        try {
            var names = esi.corporationAssetNames(corporation.eveCorporationId(), director.eveCharacterId(),
                    candidates);
            if (names.outcome() == EsiResult.Outcome.UPDATED) {
                log.info("Container names of corporation {}: asked for {}, {} have a name",
                        corporation.eveCorporationId(), candidates.size(), names.data().size());
                return names.data();
            }
            log.warn("Names of the containers of corporation {} could not be read (asked for {}): {}",
                    corporation.eveCorporationId(), candidates.size(), names.problem());
        } catch (RuntimeException e) {
            log.warn("Asking for the names of a corporation's containers failed: {}", e.getClass().getSimpleName());
        }
        return java.util.Map.of();
    }

    private void recordFailure(UUID corporationId, Instant now, String problem) {
        try {
            corporations.recordFailure(corporationId, now, problem);
        } catch (DataIntegrityViolationException gone) {
            log.debug("A corporation was removed during its asset fetch");
        }
    }

    /** Where each of the account's corporations stands, for the page. */
    public List<CorporationAssets> status(Viewer viewer) {
        if (!enabled(viewer)) {
            return List.of();
        }
        var characters = linked.of(viewer);
        var list = corporations.listFor(viewer.accountId().value());
        Map<UUID, CorporationRepository.FetchState> states = corporations.state(
                        list.stream().map(CorporationRepository.Corporation::id).toList()).stream()
                .collect(Collectors.toMap(CorporationRepository.FetchState::corporationId, Function.identity()));
        var isRunning = running.contains(viewer.accountId().value());
        return list.stream().map(corporation -> {
            var directors = characters.stream()
                    .filter(c -> c.eveCorporationId() != null && c.eveCorporationId() == corporation.eveCorporationId()
                            && Boolean.TRUE.equals(c.director()))
                    .toList();
            var usable = directors.stream().filter(this::canRead).toList();
            var state = states.get(corporation.id());
            Phase phase;
            if (isRunning) {
                phase = Phase.RUNNING;
            } else if (directors.isEmpty()) {
                phase = Phase.NO_DIRECTOR;
            } else if (usable.isEmpty()) {
                phase = Phase.NEEDS_AUTHORIZATION;
            } else if (state == null) {
                phase = Phase.NEVER;
            } else {
                phase = state.problem() == null ? Phase.CURRENT : Phase.FAILED;
            }
            return new CorporationAssets(corporation.id(), corporation.name(), phase,
                    state == null ? null : state.fetchedAt(), state == null ? null : state.itemCount(),
                    state == null ? null : state.problem(), usable.isEmpty() ? null : usable.getFirst().name(),
                    directors.size());
        }).toList();
    }

    /** True while the viewer's account is being read, so the page keeps asking for news. */
    public boolean anyRunning(Viewer viewer) {
        return running.contains(viewer.accountId().value());
    }

    /** A character that can be asked for a corporation's assets and hangar names. */
    private boolean canRead(LinkedCharacter character) {
        return usable(character, EsiClient.CORPORATION_ASSETS_SCOPE) && usable(character, EsiClient.DIVISIONS_SCOPE);
    }

    private static boolean usable(LinkedCharacter character, String scope) {
        return !character.needsReauthorization() && character.scopes().contains(scope);
    }

    @PreDestroy
    void stop() {
        executor.shutdownNow();
    }
}
