package com.eveassist.characters.internal.linking;

import java.util.Set;
import java.util.UUID;

/** A character row as stored. Never carries a token. */
public record CharacterDetails(UUID id, long eveCharacterId, String name, Set<String> scopes, String state,
        boolean hasPortrait, java.time.Instant linkedAt) {
}
