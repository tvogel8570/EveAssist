-- A plan keeps the prices it was costed with, so a quote stays the same until the user asks for new prices.
CREATE TABLE plan_price (
    plan_id bigint         NOT NULL REFERENCES plan (id) ON DELETE CASCADE,
    type_id integer        NOT NULL,
    source  text           NOT NULL DEFAULT 'market' CHECK (source IN ('market', 'manual')),
    sell    numeric(20, 2),
    buy     numeric(20, 2),
    as_of   timestamptz    NOT NULL,
    PRIMARY KEY (plan_id, type_id)
);

-- Prices no longer mark a plan out of date, and the shared price per item is replaced by jita_order.
DROP TABLE plan_costed_item;
DROP TABLE jita_price;

-- Stored totals were costed from the shared prices, which plans no longer use, so they are shown as out of date.
UPDATE plan SET costed_plan_version = NULL;
