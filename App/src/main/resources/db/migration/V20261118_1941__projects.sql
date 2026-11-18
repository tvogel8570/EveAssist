-- A project: its settings and builder, and the Draft's inputs (see architecture/projects.md). The plan provides the initial products
-- and prices, and after that the project owns them. A quoted project is locked by the application, not by these tables: its
-- tables hold the same inputs as its latest quote until a new version replaces them.
CREATE TABLE project (
    id                      bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id               uuid          NOT NULL UNIQUE,
    owner_account_id        uuid          NOT NULL REFERENCES account (public_id) ON DELETE CASCADE,
    name                    text          NOT NULL,
    -- Counts edits, for optimistic concurrency. Costing and archiving do not change it.
    version                 bigint        NOT NULL DEFAULT 0,
    status                  text          NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'quoted')),
    last_quote_number       integer       NOT NULL DEFAULT 0 CHECK (last_quote_number >= 0),
    -- Only where the project started. Deleting the plan leaves the project as it is.
    source_plan_id          bigint        REFERENCES plan (id) ON DELETE SET NULL,
    archived_at             timestamptz,
    isk_per_hour            numeric(20, 2) NOT NULL DEFAULT 0 CHECK (isk_per_hour >= 0),
    isk_per_cubic_meter     numeric(20, 2) NOT NULL DEFAULT 0 CHECK (isk_per_cubic_meter >= 0),
    contract_fee            numeric(20, 2) NOT NULL DEFAULT 10000 CHECK (contract_fee >= 0),
    quote_mode              text          NOT NULL DEFAULT 'margin' CHECK (quote_mode IN ('margin', 'market')),
    margin_percent          numeric(7, 4) NOT NULL DEFAULT 20 CHECK (margin_percent >= 0 AND margin_percent < 100),
    premium_percent         numeric(7, 4) NOT NULL DEFAULT 0 CHECK (premium_percent > -100),
    quote_step              numeric(20, 2) NOT NULL DEFAULT 1000 CHECK (quote_step >= 0),
    price_side              text          NOT NULL DEFAULT 'sell' CHECK (price_side IN ('sell', 'buy')),
    split_strategy          text          NOT NULL DEFAULT 'least_cost' CHECK (split_strategy IN ('least_cost', 'copies_first')),
    max_job_seconds         bigint        CHECK (max_job_seconds IS NULL OR max_job_seconds >= 1),
    -- Must be a facility of the same account. The application checks that, because a foreign key cannot.
    default_facility_id     bigint        REFERENCES facility (id) ON DELETE SET NULL,
    buyer_name              text,
    note                    text,
    industry_level          smallint      NOT NULL DEFAULT 0 CHECK (industry_level BETWEEN 0 AND 5),
    advanced_industry_level smallint      NOT NULL DEFAULT 0 CHECK (advanced_industry_level BETWEEN 0 AND 5),
    implant_time_percent    numeric(7, 4) NOT NULL DEFAULT 0 CHECK (implant_time_percent BETWEEN 0 AND 100),
    created_at              timestamptz   NOT NULL DEFAULT now()
);
CREATE INDEX project_owner_ix ON project (owner_account_id, name);
CREATE INDEX project_source_plan_ix ON project (source_plan_id) WHERE source_plan_id IS NOT NULL;
CREATE INDEX project_default_facility_ix ON project (default_facility_id) WHERE default_facility_id IS NOT NULL;

CREATE TABLE project_product (
    project_id bigint  NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    type_id    integer NOT NULL,
    quantity   bigint  NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (project_id, type_id)
);

-- Parts the developer chose to buy. Anything else with a blueprint is built.
CREATE TABLE project_bought_part (
    project_id bigint  NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    type_id    integer NOT NULL,
    PRIMARY KEY (project_id, type_id)
);

-- How one built item is made: typed ME and TE for now ('owned' comes with the owned blueprints, step 5).
CREATE TABLE project_item (
    project_id             bigint         NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    type_id                integer        NOT NULL,
    blueprint_kind         text           NOT NULL DEFAULT 'typed' CHECK (blueprint_kind IN ('typed', 'owned')),
    me                     smallint       NOT NULL DEFAULT 10 CHECK (me BETWEEN 0 AND 10),
    te                     smallint       NOT NULL DEFAULT 20 CHECK (te BETWEEN 0 AND 20),
    blueprint_cost_per_run numeric(20, 2) NOT NULL DEFAULT 0 CHECK (blueprint_cost_per_run >= 0),
    -- Null means the project's default facility. Deleting a facility falls back to that.
    facility_id            bigint         REFERENCES facility (id) ON DELETE SET NULL,
    -- Both null means the project's own policy. An item that overrides it sets the strategy, with or without a bound.
    split_strategy         text           CHECK (split_strategy IN ('least_cost', 'copies_first')),
    max_job_seconds        bigint         CHECK (max_job_seconds IS NULL OR max_job_seconds >= 1),
    CHECK (split_strategy IS NOT NULL OR max_job_seconds IS NULL),
    PRIMARY KEY (project_id, type_id)
);
CREATE INDEX project_item_facility_ix ON project_item (facility_id) WHERE facility_id IS NOT NULL;

-- The builder's level in each time-saving skill the item's blueprint requires. The percent per level comes from the SDE.
CREATE TABLE project_item_skill (
    project_id    bigint   NOT NULL,
    item_type_id  integer  NOT NULL,
    skill_type_id integer  NOT NULL,
    level         smallint NOT NULL CHECK (level BETWEEN 0 AND 5),
    PRIMARY KEY (project_id, item_type_id, skill_type_id),
    FOREIGN KEY (project_id, item_type_id) REFERENCES project_item (project_id, type_id) ON DELETE CASCADE
);

-- The project's own prices: copied from the plan at the start, then changed only when the developer asks.
CREATE TABLE project_price (
    project_id bigint         NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    type_id    integer        NOT NULL,
    source     text           NOT NULL DEFAULT 'market' CHECK (source IN ('market', 'manual')),
    sell       numeric(20, 2),
    buy        numeric(20, 2),
    as_of      timestamptz    NOT NULL,
    PRIMARY KEY (project_id, type_id)
);
