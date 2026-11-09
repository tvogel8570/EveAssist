package com.eveassist.characters;

import java.util.Set;
import java.util.UUID;

/**
 * A character an account has linked, as other modules see it.
 *
 * @param id                  its public id, which data stored for the character refers to
 * @param eveCharacterId      the id EVE and ESI use
 * @param scopes              what it granted, which says what ESI may be asked about it
 * @param needsReauthorization true when EVE refused its saved access, or it was dropped: no ESI call can be made
 */
public record LinkedCharacter(UUID id, long eveCharacterId, String name, Set<String> scopes,
        boolean needsReauthorization) {
}
