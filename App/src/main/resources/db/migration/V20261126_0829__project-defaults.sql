-- What a new project of an account starts from (issue #64, decided with the developer on 2026-11-25): every setting of a project except
-- the buyer and the note. The columns, their checks and their built-in values are those of project. An account has no row until it saves
-- its defaults, and none again after it resets them.
CREATE TABLE project_default (
    owner_account_id        uuid           PRIMARY KEY REFERENCES account (public_id) ON DELETE CASCADE,
    isk_per_hour            numeric(20, 2) NOT NULL CHECK (isk_per_hour >= 0),
    isk_per_cubic_meter     numeric(20, 2) NOT NULL CHECK (isk_per_cubic_meter >= 0),
    contract_fee            numeric(20, 2) NOT NULL CHECK (contract_fee >= 0),
    quote_mode              text           NOT NULL CHECK (quote_mode IN ('margin', 'market')),
    margin_percent          numeric(7, 4)  NOT NULL CHECK (margin_percent >= 0 AND margin_percent < 100),
    premium_percent         numeric(7, 4)  NOT NULL CHECK (premium_percent > -100),
    quote_step              numeric(20, 2) NOT NULL CHECK (quote_step >= 0),
    price_side              text           NOT NULL CHECK (price_side IN ('sell', 'buy', 'split')),
    split_strategy          text           NOT NULL CHECK (split_strategy IN ('least_cost', 'copies_first')),
    max_job_seconds         bigint         CHECK (max_job_seconds IS NULL OR max_job_seconds >= 1),
    -- Must be a facility of the same account. The application checks that, because a foreign key cannot.
    default_facility_id     bigint         REFERENCES facility (id) ON DELETE SET NULL,
    industry_level          smallint       NOT NULL CHECK (industry_level BETWEEN 0 AND 5),
    advanced_industry_level smallint       NOT NULL CHECK (advanced_industry_level BETWEEN 0 AND 5),
    implant_time_percent    numeric(7, 4)  NOT NULL CHECK (implant_time_percent BETWEEN 0 AND 100)
);
CREATE INDEX account_project_default_facility_ix ON project_default (default_facility_id) WHERE default_facility_id IS NOT NULL;

-- The account's level in each time-saving skill that some blueprint requires.
CREATE TABLE project_default_skill (
    owner_account_id uuid     NOT NULL REFERENCES project_default (owner_account_id) ON DELETE CASCADE,
    skill_type_id    integer  NOT NULL,
    level            smallint NOT NULL CHECK (level BETWEEN 0 AND 5),
    PRIMARY KEY (owner_account_id, skill_type_id)
);
