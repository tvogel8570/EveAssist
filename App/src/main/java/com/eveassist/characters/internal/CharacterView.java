package com.eveassist.characters.internal;

import java.util.List;
import java.util.UUID;

/**
 * A linked character as the Characters page shows it. Never carries a token.
 *
 * @param granted the scopes the character granted, sorted
 * @param inUse   the granted scopes that a turned-on feature uses, sorted
 * @param missing the scopes the turned-on features need and the character did not grant, sorted
 * @param noAccess the granted scopes in use that this character cannot use, because only a Director can read what
 *                 they give (corporation assets and hangar names). Empty while its roles are not known
 * @param unused  the scopes the character granted that no turned-on feature uses, sorted. They stay valid at EVE until
 *                the saved access is dropped, which is why the page shows them
 */
public record CharacterView(UUID id, String name, Status status, List<String> granted, List<String> inUse,
        List<String> missing, List<String> unused, boolean hasPortrait, List<String> noAccess) {

    public enum Status {
        ACTIVE,
        /** Linked and working, but a turned-on feature needs a scope it did not grant. */
        NEEDS_PERMISSIONS,
        /** No working saved access: EVE refused it, the character was sold, or the user dropped it. */
        REAUTHORIZE
    }
}
