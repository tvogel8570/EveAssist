package com.eveassist.inventory.internal;

import com.eveassist.characters.LinkedCharacter;
import com.eveassist.esi.EsiClient;
import com.eveassist.esi.StructureLookup;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Learns the names of the player structures where a character has items, after its assets are stored. ESI answers for
 * a structure only when the character may dock there and answers 403 otherwise, and every 403 counts toward ESI's error
 * limit. So a run asks about few structures, stops after a few refusals, and leaves a structure that refused alone for
 * a day. What it does not reach is picked up by the next fetch. A name is asked again after a week.
 */
@Component
class StructureResolver {

    private static final Logger log = LoggerFactory.getLogger(StructureResolver.class);

    static final int MOST_ASKED_PER_RUN = 25;
    static final int MOST_REFUSALS_PER_RUN = 10;
    static final Duration NAME_TRUSTED_FOR = Duration.ofDays(7);
    static final Duration NO_ACCESS_WAIT = Duration.ofDays(1);
    static final Duration NOT_FOUND_WAIT = Duration.ofDays(7);

    private final EsiClient esi;
    private final StructureRepository structures;
    private final Clock clock;

    StructureResolver(EsiClient esi, StructureRepository structures, Clock clock) {
        this.esi = esi;
        this.structures = structures;
        this.clock = clock;
    }

    void resolve(LinkedCharacter character) {
        if (character.needsReauthorization() || !character.scopes().contains(EsiClient.STRUCTURES_SCOPE)) {
            return;
        }
        var now = clock.instant();
        var refusals = 0;
        for (var structureId : structures.toAsk(character.id(), now, NAME_TRUSTED_FOR, NO_ACCESS_WAIT,
                NOT_FOUND_WAIT, MOST_ASKED_PER_RUN)) {
            StructureLookup answer;
            try {
                answer = esi.structure(character.eveCharacterId(), structureId);
            } catch (RuntimeException e) {
                log.warn("Asking for a structure name failed: {}", e.getClass().getSimpleName());
                return;
            }
            if (answer.status() == StructureLookup.Status.FAILED) {
                // throttled, down, or no usable token: more asks would fail the same way
                log.debug("Structure names stopped early: {}", answer.problem());
                return;
            }
            structures.save(character.id(), structureId, answer, clock.instant());
            if (answer.status() != StructureLookup.Status.FOUND) {
                log.info("ESI answered {} for structure {} and a character: {}", answer.status(), structureId,
                        answer.problem());
            }
            if (answer.status() == StructureLookup.Status.NO_ACCESS) {
                refusals++;
                if (refusals >= MOST_REFUSALS_PER_RUN) {
                    return;
                }
            }
        }
    }
}
