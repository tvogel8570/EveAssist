package com.eveassist.characters.internal;

import com.eveassist.esi.EsiClient;
import com.eveassist.esi.EsiResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Reads from ESI which corporation a character is in and whether it has the Director role, for the characters that
 * granted the roles permission. An answer is kept for as long as ESI caches it, so asking again is free: the roles for
 * an hour, the corporation for a day. A failed lookup keeps what was known and is never retried in a loop.
 */
@Component
class MembershipLearner {

    private static final Logger log = LoggerFactory.getLogger(MembershipLearner.class);

    /** ESI caches a character's roles for an hour. */
    static final Duration ROLES_TRUSTED_FOR = Duration.ofHours(1);
    /** ESI caches the public character, which names its corporation, for a day. */
    static final Duration CORPORATION_TRUSTED_FOR = Duration.ofDays(1);

    private final EsiClient esi;
    private final MembershipRepository memberships;
    private final Clock clock;

    MembershipLearner(EsiClient esi, MembershipRepository memberships, Clock clock) {
        this.esi = esi;
        this.memberships = memberships;
        this.clock = clock;
    }

    /** Learns the characters that can say and are due. */
    void learn(List<CharacterDetails> characters) {
        for (var character : characters) {
            if (!"NEEDS_REAUTHORIZATION".equals(character.state()) && character.scopes().contains(EsiClient.ROLES_SCOPE)) {
                learn(character);
            }
        }
    }

    private void learn(CharacterDetails character) {
        var now = clock.instant();
        var known = memberships.find(character.id());
        if (known.isPresent() && known.get().checkedAt().plus(ROLES_TRUSTED_FOR).isAfter(now)) {
            return;
        }
        try {
            long corporation;
            Instant corporationAskedAt;
            if (known.isPresent() && known.get().corporationCheckedAt().plus(CORPORATION_TRUSTED_FOR).isAfter(now)) {
                corporation = known.get().eveCorporationId();
                corporationAskedAt = known.get().corporationCheckedAt();
            } else {
                var asked = esi.characterCorporationId(character.eveCharacterId());
                if (asked.outcome() != EsiResult.Outcome.UPDATED) {
                    log.debug("A character's corporation could not be learned: {}", asked.problem());
                    return;
                }
                corporation = asked.data();
                corporationAskedAt = now;
            }
            var roles = esi.characterRoles(character.eveCharacterId());
            if (roles.outcome() != EsiResult.Outcome.UPDATED) {
                log.debug("A character's roles could not be learned: {}", roles.problem());
                return;
            }
            memberships.save(character.id(), corporation, roles.data().contains("Director"), now, corporationAskedAt);
        } catch (DataIntegrityViolationException gone) {
            log.debug("A character was removed while its corporation was being learned");
        }
    }
}
