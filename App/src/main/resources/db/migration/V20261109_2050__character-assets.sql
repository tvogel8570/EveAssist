-- A character's assets as ESI last gave them. Each fetch replaces the character's rows (no history), and removing the
-- character removes them. Only the fields a feature uses are kept (see gain-trust.md).
CREATE TABLE character_asset (
    character_id   uuid    NOT NULL REFERENCES character (public_id) ON DELETE CASCADE,
    item_id        bigint  NOT NULL,
    type_id        integer NOT NULL,
    quantity       bigint  NOT NULL,
    -- a station, structure or solar system id for an item at the top, otherwise the item_id of its container
    location_id    bigint  NOT NULL,
    location_type  text    NOT NULL,
    location_flag  text    NOT NULL,
    is_singleton   boolean NOT NULL,
    -- true or false for blueprints, null for everything else
    blueprint_copy boolean,
    PRIMARY KEY (character_id, item_id)
);
CREATE INDEX character_asset_type_ix ON character_asset (type_id);

-- When each kind of data was last fetched for a character, and how that went. It backs the "fetched N minutes ago"
-- labels and survives a restart. Stored data stays on screen when a fetch fails.
CREATE TABLE character_fetch (
    character_id uuid        NOT NULL REFERENCES character (public_id) ON DELETE CASCADE,
    kind         text        NOT NULL,
    -- the last time ESI answered, with new data or with "unchanged"
    fetched_at   timestamptz,
    -- the last time the stored data was replaced
    changed_at   timestamptz,
    attempted_at timestamptz NOT NULL,
    -- null when the last attempt worked, otherwise a short reason
    problem      text,
    item_count   integer,
    PRIMARY KEY (character_id, kind)
);
