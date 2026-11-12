-- Where each asset sits, worked out once when the assets are stored (the app finds the top of each container chain)
-- so that the inventory page can group, filter and count without walking chains for every request.
--
-- Assets and their fetch records are re-read from ESI, so the old rows are dropped rather than converted: the next
-- visit to the inventory page reads them again. The cached ESI answers go too, or ESI would say "unchanged" and
-- the rows would stay empty.
DELETE FROM esi_cache WHERE cache_key LIKE 'characters/%/assets';
DELETE FROM character_asset;
DELETE FROM character_fetch WHERE kind = 'ASSETS';

ALTER TABLE character_asset
    -- the station, structure or solar system at the top of the item's container chain
    ADD COLUMN root_location_id  bigint   NOT NULL,
    -- 0 for an item at the top, 1 for an item in a container or ship at the top, and so on
    ADD COLUMN depth             smallint NOT NULL CHECK (depth >= 0),
    -- the type of the container or ship that holds the item directly, null at the top
    ADD COLUMN container_type_id integer,
    -- HANGAR, IN_CONTAINER, IN_SHIP, FITTED, DELIVERIES or ASSET_SAFETY
    ADD COLUMN held              text     NOT NULL
        CHECK (held IN ('HANGAR', 'IN_CONTAINER', 'IN_SHIP', 'FITTED', 'DELIVERIES', 'ASSET_SAFETY'));

CREATE INDEX character_asset_root_ix ON character_asset (character_id, root_location_id);
