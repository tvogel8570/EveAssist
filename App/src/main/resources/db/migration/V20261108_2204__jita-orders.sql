-- The orders at Jita 4-4 as ESI last gave them, per item, so that a price can be worked out for any share of the volume.
CREATE TABLE jita_order (
    order_id      bigint         PRIMARY KEY,
    type_id       integer        NOT NULL,
    is_buy        boolean        NOT NULL,
    price         numeric(20, 2) NOT NULL,
    volume_remain bigint         NOT NULL
);
CREATE INDEX jita_order_type_ix ON jita_order (type_id);

-- When each item's orders were last confirmed current. An item with no orders at Jita 4-4 has no rows above.
CREATE TABLE jita_fetch (
    type_id    integer     PRIMARY KEY,
    fetched_at timestamptz NOT NULL
);
