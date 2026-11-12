-- Public item exchange contracts in a region, shared by all users. Used to price things the market does not sell,
-- such as blueprint copies. A contract's lines never change, so they are read once.
CREATE TABLE contract_public (
    contract_id       bigint           PRIMARY KEY,
    region_id         integer          NOT NULL,
    price             numeric(20, 2)   NOT NULL,
    issued_at         timestamptz      NOT NULL,
    expires_at        timestamptz      NOT NULL,
    start_location_id bigint,
    volume            double precision NOT NULL,
    items_fetched     boolean          NOT NULL DEFAULT false
);
CREATE INDEX contract_public_pending_ix ON contract_public (issued_at DESC) WHERE NOT items_fetched;

CREATE TABLE contract_public_item (
    contract_id bigint   NOT NULL REFERENCES contract_public (contract_id) ON DELETE CASCADE,
    record_id   bigint   NOT NULL,
    type_id     integer  NOT NULL,
    quantity    bigint   NOT NULL,
    included    boolean  NOT NULL,
    is_copy     boolean  NOT NULL,
    me          smallint,
    te          smallint,
    runs        integer,
    PRIMARY KEY (contract_id, record_id)
);
CREATE INDEX contract_public_item_copy_ix ON contract_public_item (type_id) WHERE is_copy;

-- When each region's contract list was last confirmed current.
CREATE TABLE contract_public_fetch (
    region_id  integer     PRIMARY KEY,
    fetched_at timestamptz NOT NULL
);
