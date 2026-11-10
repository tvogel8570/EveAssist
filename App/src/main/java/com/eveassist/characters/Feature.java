package com.eveassist.characters;

import java.util.Set;

/**
 * A feature that needs ESI scopes. An account turns features on, and linking asks for the scopes of the turned-on
 * ones only. All scopes are read-only (see gain-trust.md).
 */
public enum Feature {

    INVENTORY("esi-assets.read_assets.v1", "esi-universe.read_structures.v1"),

    /**
     * The assets of the corporations the account's own characters belong to. Only a character with the Director role
     * can read them, and the roles scope says which characters those are. It builds on {@link #INVENTORY}.
     */
    CORPORATION_ASSETS("esi-assets.read_corporation_assets.v1", "esi-corporations.read_divisions.v1",
            "esi-characters.read_corporation_roles.v1");

    private final Set<String> scopes;

    Feature(String... scopes) {
        this.scopes = Set.of(scopes);
    }

    public Set<String> scopes() {
        return scopes;
    }

    /** The feature this one builds on, or null. Turning this one on turns that one on, and turning that one off turns this one off. */
    public Feature requires() {
        return this == CORPORATION_ASSETS ? INVENTORY : null;
    }
}
