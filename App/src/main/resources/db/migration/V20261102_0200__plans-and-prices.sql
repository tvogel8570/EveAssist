CREATE TABLE jita_price (
    type_id    integer        PRIMARY KEY,
    sell_price numeric(20, 2) NOT NULL,
    as_of      timestamptz    NOT NULL
);

CREATE TABLE plan (
    id                  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id           uuid           NOT NULL UNIQUE,
    owner_account_id    uuid           NOT NULL REFERENCES account (public_id) ON DELETE CASCADE,
    name                text           NOT NULL,
    version             bigint         NOT NULL DEFAULT 0,
    total_cost          numeric(24, 2),
    costed_plan_version bigint,
    costed_sde_version  text,
    prices_as_of        timestamptz,
    costed_at           timestamptz,
    created_at          timestamptz    NOT NULL DEFAULT now()
);
CREATE INDEX plan_owner_ix ON plan (owner_account_id, name);

CREATE TABLE plan_product (
    plan_id  bigint  NOT NULL REFERENCES plan (id) ON DELETE CASCADE,
    type_id  integer NOT NULL,
    quantity bigint  NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (plan_id, type_id)
);
