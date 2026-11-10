-- The assets of the corporations an account's own characters belong to (phase 1: one account, no sharing). The account
-- owns the rows, and each carries the corporation id, so that a group can later share them without a change of what is
-- stored here (see esi.md, Corporation data).

-- The corporation a linked character belongs to, and whether it has the Director role there. Read from ESI and kept
-- for as long as ESI caches each answer, so that opening the page does not ask every time: the roles for an hour, the
-- corporation for a day.
CREATE TABLE character_corporation (
    character_id       uuid        PRIMARY KEY REFERENCES character (public_id) ON DELETE CASCADE,
    eve_corporation_id bigint      NOT NULL,
    director           boolean     NOT NULL,
    -- when the roles were last asked for
    checked_at         timestamptz NOT NULL,
    -- when the corporation was last asked for
    corporation_checked_at timestamptz NOT NULL
);

CREATE TABLE corporation (
    public_id          uuid        PRIMARY KEY,
    owner_account_id   uuid        NOT NULL REFERENCES account (public_id) ON DELETE CASCADE,
    eve_corporation_id bigint      NOT NULL,
    name               text        NOT NULL,
    -- when the name, and the names of the hangar divisions, were last asked for
    named_at           timestamptz NOT NULL,
    divisions_at       timestamptz,
    UNIQUE (owner_account_id, eve_corporation_id)
);

-- The names the corporation gave its seven hangar divisions (1 to 7).
CREATE TABLE corporation_division (
    corporation_id uuid     NOT NULL REFERENCES corporation (public_id) ON DELETE CASCADE,
    division       smallint NOT NULL CHECK (division BETWEEN 1 AND 7),
    name           text     NOT NULL,
    PRIMARY KEY (corporation_id, division)
);

-- Like character_asset. Each fetch replaces the corporation's rows (no history). An office is not stored: it is a
-- folder, and the items in it are at the station or structure it stands in, in the hangar division the flag names.
CREATE TABLE corporation_asset (
    corporation_id    uuid     NOT NULL REFERENCES corporation (public_id) ON DELETE CASCADE,
    item_id           bigint   NOT NULL,
    type_id           integer  NOT NULL,
    quantity          bigint   NOT NULL,
    location_id       bigint   NOT NULL,
    location_type     text     NOT NULL,
    location_flag     text     NOT NULL,
    is_singleton      boolean  NOT NULL,
    blueprint_copy    boolean,
    root_location_id  bigint   NOT NULL,
    depth             smallint NOT NULL CHECK (depth >= 0),
    container_type_id integer,
    held              text     NOT NULL
        CHECK (held IN ('HANGAR', 'IN_CONTAINER', 'IN_SHIP', 'FITTED', 'DELIVERIES', 'ASSET_SAFETY')),
    -- the hangar division (1 to 7) the item, or the container that holds it, is in. Null outside an office.
    division          smallint CHECK (division BETWEEN 1 AND 7),
    PRIMARY KEY (corporation_id, item_id)
);
CREATE INDEX corporation_asset_type_ix ON corporation_asset (type_id);
CREATE INDEX corporation_asset_root_ix ON corporation_asset (corporation_id, root_location_id);

CREATE TABLE corporation_fetch (
    corporation_id uuid        NOT NULL REFERENCES corporation (public_id) ON DELETE CASCADE,
    kind           text        NOT NULL,
    fetched_at     timestamptz,
    changed_at     timestamptz,
    attempted_at   timestamptz NOT NULL,
    problem        text,
    item_count     integer,
    PRIMARY KEY (corporation_id, kind)
);

-- What the inventory page browses: the items of characters and of corporations, one row each, under the id of the
-- character or corporation that holds them. Every query is limited to ids of the viewer's own holders.
CREATE VIEW holder_asset AS
    SELECT character_id AS holder_id, type_id, quantity, root_location_id, held, blueprint_copy,
           NULL::smallint AS division
    FROM character_asset
    UNION ALL
    SELECT corporation_id, type_id, quantity, root_location_id, held, blueprint_copy, division
    FROM corporation_asset;
