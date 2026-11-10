package com.eveassist.characters;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * A character an account has linked, as other modules see it.
 *
 * @param id                  its public id, which data stored for the character refers to
 * @param eveCharacterId      the id EVE and ESI use
 * @param scopes              what it granted, which says what ESI may be asked about it
 * @param needsReauthorization true when EVE refused its saved access, or it was dropped: no ESI call can be made
 * @param linkedAt            when it was first linked, which gives it a place in the user's characters that stays put
 */
public record LinkedCharacter(UUID id, long eveCharacterId, String name, Set<String> scopes,
        boolean needsReauthorization, Instant linkedAt) {

    /** For a character whose link time does not matter, such as in a test. */
    public LinkedCharacter(UUID id, long eveCharacterId, String name, Set<String> scopes, boolean needsReauthorization) {
        this(id, eveCharacterId, name, scopes, needsReauthorization, Instant.EPOCH);
    }
}
