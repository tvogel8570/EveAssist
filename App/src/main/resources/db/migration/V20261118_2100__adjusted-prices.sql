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

-- One row: when the prices were last known to be ESI's current copy. A refresh that finds ESI's copy unchanged only moves this.
CREATE TABLE adjusted_price_fetch (
    id         boolean     PRIMARY KEY DEFAULT true CHECK (id),
    fetched_at timestamptz NOT NULL
);
