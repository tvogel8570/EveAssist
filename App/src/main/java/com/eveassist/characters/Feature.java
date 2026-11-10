package com.eveassist.characters;

import java.util.Set;

/**
 * A feature that needs ESI scopes. An account turns features on, and linking asks for the scopes of the turned-on
 * ones only. All scopes are read-only (see gain-trust.md).
 */
public enum Feature {

    INVENTORY("esi-assets.read_assets.v1", "esi-universe.read_structures.v1");

    private final Set<String> scopes;

    Feature(String... scopes) {
        this.scopes = Set.of(scopes);
    }

    public Set<String> scopes() {
        return scopes;
    }
}
