package com.eveassist.characters.internal;

import java.util.Set;
import java.util.UUID;

/** A character row as stored. Never carries a token. */
record CharacterDetails(UUID id, long eveCharacterId, String name, Set<String> scopes, String state,
        boolean hasPortrait) {
}
