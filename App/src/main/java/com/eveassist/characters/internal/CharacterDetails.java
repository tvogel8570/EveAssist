package com.eveassist.characters.internal;

import java.util.Set;
import java.util.UUID;

/** A linked character as the Characters page shows it. Never carries a token. */
record CharacterDetails(UUID id, long eveCharacterId, String name, Set<String> scopes, String state) {
}
