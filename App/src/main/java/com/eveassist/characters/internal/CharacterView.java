package com.eveassist.characters.internal;

import java.util.List;
import java.util.UUID;

/**
 * A linked character as the Characters page shows it. Never carries a token.
 *
 * @param granted the scopes the character granted, sorted
 * @param missing the scopes the turned-on features need and the character did not grant, sorted
 */
public record CharacterView(UUID id, String name, Status status, List<String> granted, List<String> missing,
        boolean hasPortrait) {

    public enum Status {
        ACTIVE,
        /** Linked and working, but a turned-on feature needs a scope it did not grant. */
        NEEDS_PERMISSIONS,
        /** EVE refused its refresh token, or it was sold. Only linking it again helps. */
        REAUTHORIZE
    }
}
