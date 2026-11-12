-- Things in an office the corporation lost (the office has the flag Impounded) are now held as IMPOUNDED, and the office,
-- which is a folder like any other, is no longer stored as an item. `held` and the place of each item are worked out when
-- the assets are stored, so the corporations' assets are dropped and read again at the next visit to the inventory page,
-- as the asset-places migration did. The cached ESI answers go too, or ESI would say "unchanged" and the rows would stay
-- empty.
ALTER TABLE character_asset   DROP CONSTRAINT character_asset_held_check;
ALTER TABLE character_asset   ADD CONSTRAINT character_asset_held_check
    CHECK (held IN ('HANGAR', 'IN_CONTAINER', 'IN_SHIP', 'FITTED', 'DELIVERIES', 'ASSET_SAFETY', 'IMPOUNDED'));
ALTER TABLE corporation_asset DROP CONSTRAINT corporation_asset_held_check;
ALTER TABLE corporation_asset ADD CONSTRAINT corporation_asset_held_check
    CHECK (held IN ('HANGAR', 'IN_CONTAINER', 'IN_SHIP', 'FITTED', 'DELIVERIES', 'ASSET_SAFETY', 'IMPOUNDED'));

DELETE FROM esi_cache WHERE cache_key LIKE 'corporations/%/assets';
DELETE FROM corporation_asset;
DELETE FROM corporation_fetch WHERE kind = 'ASSETS';
