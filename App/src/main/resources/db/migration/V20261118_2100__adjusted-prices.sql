-- CCP's adjusted and average price of every item type (ESI GET /markets/prices), shared by all users: public data, so there is no owner.
-- A job's estimated item value, and so its install cost, is worked out from the adjusted price. The whole table is replaced when ESI's
-- copy changes (about 16,000 rows, ESI caches it for an hour).
--
-- Both prices are optional because ESI's spec requires only the type. An adjusted price of 0 is a real value that the game uses as it is,
-- and about half of today's rows are 0, so it is not a gap. A type with no row at all is.
-- numeric with no scale, because the digits ESI sends are kept (the game's value matches them to the ISK).
CREATE TABLE adjusted_price (
    type_id        integer PRIMARY KEY,
    adjusted_price numeric CHECK (adjusted_price >= 0),
    average_price  numeric CHECK (average_price >= 0)
);

-- One row with two times, which mean different things (the developer asked for both on 2026-11-18, not knowing yet which a screen needs):
--   fetched_at         when the prices were last confirmed to be ESI's current copy, by a call to ESI or by ESI's own cache time. It moves
--                      forward every time a project is costed and nothing has changed.
--   source_modified_at ESI's Last-Modified header for the list, as sent (null if none). Not a reliable age: on 2026-11-18 it was 20 minutes
--                      ahead of ESI's own Date header and moved by two hours between readings. Kept so a screen may show it.
CREATE TABLE adjusted_price_fetch (
    id                 boolean     PRIMARY KEY DEFAULT true CHECK (id),
    fetched_at         timestamptz NOT NULL,
    source_modified_at timestamptz
);
