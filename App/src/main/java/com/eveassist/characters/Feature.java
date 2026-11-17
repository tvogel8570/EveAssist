package com.eveassist.characters;

import java.util.Set;

/**
 * A feature that needs ESI scopes. An account turns features on, and linking asks for the scopes of the turned-on
 * ones only. All scopes are read-only (see gain-trust.md).
 */
public enum Feature {

    INVENTORY(null, "esi-assets.read_assets.v1", "esi-universe.read_structures.v1"),

    /** Blueprints held by the account's characters, matched to their inventory rows. */
    BLUEPRINTS(INVENTORY, "esi-characters.read_blueprints.v1"),

    /**
     * The assets of the corporations the account's own characters belong to. Only a character with the Director role
     * can read them, and the roles scope says which characters those are. It builds on {@link #INVENTORY}.
     */
    CORPORATION_ASSETS(INVENTORY, "esi-assets.read_corporation_assets.v1", "esi-corporations.read_divisions.v1",
            "esi-characters.read_corporation_roles.v1"),

    /** Blueprints held by the account's corporations, read through a Director alongside corporation assets. */
    CORPORATION_BLUEPRINTS(CORPORATION_ASSETS, "esi-corporations.read_blueprints.v1");

    private final Feature requires;
    private final Set<String> scopes;

    Feature(Feature requires, String... scopes) {
        this.requires = requires;
        this.scopes = Set.of(scopes);
    }

    public Set<String> scopes() {
        return scopes;
    }

    /** The feature this one builds on, or null. */
    public Feature requires() {
        return requires;
    }
}
