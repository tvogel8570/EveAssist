package com.eveassist.inventory.internal;

import com.eveassist.characters.Feature;
import com.eveassist.characters.LinkedCharacter;
import com.eveassist.characters.LinkedCharacters;
import com.eveassist.esi.EsiClient;
import com.eveassist.esi.EsiResult;
import com.eveassist.identity.Viewer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** Fetches the viewer's character and corporation blueprints in the background, using ESI's own cache timers. */
@Service
public class BlueprintFetcher {

    private static final Logger log = LoggerFactory.getLogger(BlueprintFetcher.class);
    /** The name this route has in {@link DirectorHistory}. */
    private static final String ROUTE = "blueprints";

    public enum Phase {
        NEVER, RUNNING, CURRENT, FAILED, NEEDS_REAUTHORIZATION, MISSING_SCOPE
    }

    /** What the collapsed status line says: whether a read is running, whether a holder needs attention, or all is well. */
    public enum SummaryState { RUNNING, ATTENTION, NEVER, OK }

    public record Summary(SummaryState state, int holders, int needAttention, Instant lastRead) {
    }

    /** The one line the page shows until the viewer asks for the table of holders. */
    public static Summary summarize(List<HolderStatus> statuses) {
        var attention = (int) statuses.stream().filter(status -> status.phase() == Phase.FAILED
                || status.phase() == Phase.NEEDS_REAUTHORIZATION || status.phase() == Phase.MISSING_SCOPE).count();
        var lastRead = statuses.stream().map(HolderStatus::fetchedAt).filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
        var state = statuses.stream().anyMatch(status -> status.phase() == Phase.RUNNING) ? SummaryState.RUNNING
                : attention > 0 ? SummaryState.ATTENTION : lastRead == null ? SummaryState.NEVER : SummaryState.OK;
        return new Summary(state, statuses.size(), attention, lastRead);
    }

    public record HolderStatus(UUID holderId, String name, boolean corporation, Phase phase, Instant fetchedAt,
            Integer itemCount, String problem, String via) {
    }

    private final LinkedCharacters linked;
    private final EsiClient esi;
    private final BlueprintRepository blueprints;
    private final CorporationRepository corporations;
    private final CorporationFetcher corporationFetcher;
    private final AssetFetcher assetFetcher;
    private final DirectorHistory directorHistory;
    private final Clock clock;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Set<UUID> characterRunning = ConcurrentHashMap.newKeySet();
    private final Set<UUID> accountsRunning = ConcurrentHashMap.newKeySet();
    private final Timer turnaround;

    BlueprintFetcher(LinkedCharacters linked, EsiClient esi, BlueprintRepository blueprints,
            CorporationRepository corporations, CorporationFetcher corporationFetcher, AssetFetcher assetFetcher,
            DirectorHistory directorHistory, Clock clock, MeterRegistry meters) {
        this.directorHistory = directorHistory;
        this.linked = linked;
        this.esi = esi;
        this.blueprints = blueprints;
        this.corporations = corporations;
        this.corporationFetcher = corporationFetcher;
        this.assetFetcher = assetFetcher;
        this.clock = clock;
        turnaround = Timer.builder("eveassist.blueprint.fetch.turnaround")
                .description("Time from a character's blueprints being queued to the fetch being done")
                .register(meters);
    }

    /** Queues the account's blueprint reads and asset refreshes, then returns without waiting for ESI. */
    public void refresh(Viewer viewer) {
        refresh(viewer, false);
    }

    /**
     * As above. With {@code retryFailedDirectors}, which a button press asks for, a Director who recently failed is
     * asked again at once instead of after a while.
     */
    public void refresh(Viewer viewer, boolean retryFailedDirectors) {
        var features = linked.featuresOn(viewer);
        if (!features.contains(Feature.BLUEPRINTS) && !features.contains(Feature.CORPORATION_BLUEPRINTS)) {
            return;
        }
        if (features.contains(Feature.INVENTORY)) {
            assetFetcher.refresh(viewer);
        }
        var characters = linked.of(viewer);
        if (features.contains(Feature.BLUEPRINTS)) {
            for (var character : characters) {
                if (canReadCharacter(character) && characterRunning.add(character.id())) {
                    var queued = Timer.start();
                    executor.execute(() -> {
                        try {
                            fetchCharacter(character);
                        } finally {
                            characterRunning.remove(character.id());
                            queued.stop(turnaround);
                        }
                    });
                }
            }
        }
        var accountId = viewer.accountId().value();
        if (features.contains(Feature.CORPORATION_BLUEPRINTS) && accountsRunning.add(accountId)) {
            executor.execute(() -> {
                try {
                    fetchCorporations(viewer, retryFailedDirectors);
                } finally {
                    accountsRunning.remove(accountId);
                }
            });
        }
    }

    private void fetchCharacter(LinkedCharacter character) {
        var now = clock.instant();
        EsiResult<List<com.eveassist.esi.CharacterBlueprint>> result;
        try {
            result = esi.characterBlueprints(character.eveCharacterId());
            if (result.outcome() == EsiResult.Outcome.UNCHANGED && !blueprints.everStoredCharacter(character.id())) {
                esi.forgetCharacter(character.eveCharacterId());
                result = esi.characterBlueprints(character.eveCharacterId());
            }
            switch (result.outcome()) {
                case UPDATED -> blueprints.replaceCharacter(character.id(), result.data(), now);
                case UNCHANGED -> blueprints.unchangedCharacter(character.id(), now);
                case FAILED -> blueprints.failedCharacter(character.id(), now, result.problem());
            }
        } catch (RuntimeException e) {
            log.warn("Fetching the blueprints of a character failed: {}", e.getClass().getSimpleName());
            recordCharacterFailure(character.id(), now, "The fetch failed unexpectedly");
        }
    }

    /** Learns corporation ownership and assets first, then reads each corporation through one of its Directors. */
    private void fetchCorporations(Viewer viewer, boolean retryFailedDirectors) {
        try {
            corporationFetcher.runGuarded(viewer, BlueprintFetcher::canReadCorporation);
        } catch (RuntimeException e) {
            log.warn("Preparing corporation blueprints failed: {}", e.getClass().getSimpleName());
        }
        var characters = linked.of(viewer);
        for (var corporation : corporations.listFor(viewer.accountId().value())) {
            var directors = readableDirectors(characters, corporation);
            if (directors.isEmpty()) {
                continue;
            }
            if (retryFailedDirectors) {
                directorHistory.forgetFailures(corporation.id(), ROUTE);
            }
            fetchCorporation(corporation.id(), corporation.eveCorporationId(), directors);
        }
    }

    private void fetchCorporation(UUID corporationId, long eveCorporationId, List<LinkedCharacter> directors) {
        String lastProblem = "A Director could not read corporation blueprints";
        var ordered = directorHistory.order(corporationId, ROUTE, directors);
        if (ordered.isEmpty()) {
            // every Director failed a moment ago while another worked: what is stored stays, and they are asked later
            return;
        }
        var failedBefore = new java.util.ArrayList<LinkedCharacter>();
        for (var director : ordered) {
            var now = clock.instant();
            try {
                var result = esi.corporationBlueprints(eveCorporationId, director.eveCharacterId());
                if (result.outcome() == EsiResult.Outcome.UNCHANGED && !blueprints.everStoredCorporation(corporationId)) {
                    esi.forgetCorporation(eveCorporationId);
                    result = esi.corporationBlueprints(eveCorporationId, director.eveCharacterId());
                }
                switch (result.outcome()) {
                    case UPDATED -> {
                        blueprints.replaceCorporation(corporationId, result.data(), now);
                        directorHistory.worked(corporationId, ROUTE, director, failedBefore);
                        return;
                    }
                    case UNCHANGED -> {
                        blueprints.unchangedCorporation(corporationId, now);
                        directorHistory.worked(corporationId, ROUTE, director, failedBefore);
                        return;
                    }
                    case FAILED -> {
                        lastProblem = result.problem();
                        failedBefore.add(director);
                    }
                }
            } catch (RuntimeException e) {
                log.warn("Fetching the blueprints of a corporation failed: {}", e.getClass().getSimpleName());
                lastProblem = "The fetch failed unexpectedly";
            }
        }
        try {
            blueprints.failedCorporation(corporationId, clock.instant(), lastProblem);
        } catch (DataIntegrityViolationException gone) {
            log.debug("A corporation was removed during its blueprint fetch");
        }
    }

    private void recordCharacterFailure(UUID characterId, Instant now, String problem) {
        try {
            blueprints.failedCharacter(characterId, now, problem);
        } catch (DataIntegrityViolationException gone) {
            log.debug("A character was removed during its blueprint fetch");
        }
    }

    /** Fetch phase for each holder the viewer is permitted to see. */
    public List<HolderStatus> status(Viewer viewer) {
        var features = linked.featuresOn(viewer);
        var characters = linked.of(viewer);
        var result = new ArrayList<HolderStatus>();
        if (features.contains(Feature.BLUEPRINTS)) {
            var states = blueprints.characterStates(characters.stream().map(LinkedCharacter::id).toList()).stream()
                    .collect(Collectors.toMap(BlueprintRepository.FetchState::holderId, state -> state));
            for (var character : characters) {
                var state = states.get(character.id());
                var phase = characterRunning.contains(character.id()) ? Phase.RUNNING
                        : !canReadCharacter(character) ? (character.needsReauthorization()
                                ? Phase.NEEDS_REAUTHORIZATION : Phase.MISSING_SCOPE)
                        : state == null ? Phase.NEVER : state.problem() == null ? Phase.CURRENT : Phase.FAILED;
                result.add(new HolderStatus(character.id(), character.name(), false, phase,
                        state == null ? null : state.fetchedAt(), state == null ? null : state.itemCount(),
                        state == null ? null : state.problem(), null));
            }
        }
        if (features.contains(Feature.CORPORATION_BLUEPRINTS)) {
            var own = corporationsWithDirector(viewer);
            var states = blueprints.corporationStates(own.stream().map(CorporationRepository.Corporation::id).toList())
                    .stream().collect(Collectors.toMap(BlueprintRepository.FetchState::holderId, state -> state));
            for (var corporation : own) {
                var state = states.get(corporation.id());
                var phase = accountsRunning.contains(viewer.accountId().value()) ? Phase.RUNNING
                        : state == null ? Phase.NEVER : state.problem() == null ? Phase.CURRENT : Phase.FAILED;
                var directors = readableDirectors(characters, corporation);
                result.add(new HolderStatus(corporation.id(), corporation.name(), true, phase,
                        state == null ? null : state.fetchedAt(), state == null ? null : state.itemCount(),
                        state == null ? null : state.problem(), directors.getFirst().name()));
            }
        }
        return List.copyOf(result);
    }

    public boolean anyRunning(Viewer viewer) {
        return !characterRunning.isEmpty() && linked.of(viewer).stream().anyMatch(c -> characterRunning.contains(c.id()))
                || accountsRunning.contains(viewer.accountId().value()) || assetFetcher.anyRunning(viewer)
                || corporationFetcher.anyRunning(viewer);
    }

    private static boolean canReadCharacter(LinkedCharacter character) {
        return !character.needsReauthorization() && character.scopes().contains(EsiClient.CHARACTER_BLUEPRINTS_SCOPE);
    }

    /**
     * The viewer's corporations that have a Director who can read corporation blueprints. The others are not shown and
     * ESI is not asked about them: without such a Director there is nothing to read.
     */
    public List<CorporationRepository.Corporation> corporationsWithDirector(Viewer viewer) {
        var characters = linked.of(viewer);
        return corporations.listFor(viewer.accountId().value()).stream()
                .filter(corporation -> !readableDirectors(characters, corporation).isEmpty()).toList();
    }

    private static List<LinkedCharacter> readableDirectors(List<LinkedCharacter> characters,
            CorporationRepository.Corporation corporation) {
        return characters.stream().filter(character -> character.eveCorporationId() != null
                && character.eveCorporationId() == corporation.eveCorporationId()
                && Boolean.TRUE.equals(character.director()) && canReadCorporation(character)).toList();
    }

    static boolean canReadCorporation(LinkedCharacter character) {
        return !character.needsReauthorization() && character.scopes().contains(EsiClient.CORPORATION_BLUEPRINTS_SCOPE);
    }

    @PreDestroy
    void stop() {
        executor.shutdownNow();
    }
}