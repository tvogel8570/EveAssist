-- A container or ship is an asset too, and the items in it point at it. Its name, when the player gave it one, is read
-- with the assets (POST .../assets/names) and kept on its own row, so a where-line can say which container holds an item.
ALTER TABLE character_asset   ADD COLUMN name text;
ALTER TABLE corporation_asset ADD COLUMN name text;

-- The browse view gains what a where-line needs to name the container: the id of the item that holds this one directly
-- (null at the top of a chain, and for items directly in a corporation's office), its type, and the item's own name.
-- Columns are only added at the end, so existing queries are unaffected.
CREATE OR REPLACE VIEW holder_asset AS
    SELECT character_id AS holder_id, type_id, quantity, root_location_id, held, blueprint_copy,
           NULL::smallint AS division,
           item_id, CASE WHEN depth >= 1 THEN location_id END AS container_id, container_type_id, name
    FROM character_asset
    UNION ALL
    SELECT corporation_id, type_id, quantity, root_location_id, held, blueprint_copy, division,
           item_id, CASE WHEN depth >= 1 THEN location_id END, container_type_id, name
    FROM corporation_asset;
