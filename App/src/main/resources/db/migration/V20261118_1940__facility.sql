-- A facility the developer builds in, entered by hand (see architecture/projects.md). Every column is what `FacilityTerms` takes,
-- as a whole number of percent (1.5 is 1.5%). Rig percents are already scaled for the system's security, and the cost index is
-- typed (ESI has none for wormholes), with decimals. Step 3 adds the structure type, rigs and system as more columns.
CREATE TABLE facility (
    id                         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id                  uuid          NOT NULL UNIQUE,
    owner_account_id           uuid          NOT NULL REFERENCES account (public_id) ON DELETE CASCADE,
    name                       text          NOT NULL,
    structure_material_percent numeric(7, 4) NOT NULL DEFAULT 0 CHECK (structure_material_percent BETWEEN 0 AND 100),
    rig_material_percent       numeric(7, 4) NOT NULL DEFAULT 0 CHECK (rig_material_percent BETWEEN 0 AND 100),
    structure_time_percent     numeric(7, 4) NOT NULL DEFAULT 0 CHECK (structure_time_percent BETWEEN 0 AND 100),
    rig_time_percent           numeric(7, 4) NOT NULL DEFAULT 0 CHECK (rig_time_percent BETWEEN 0 AND 100),
    structure_cost_percent     numeric(7, 4) NOT NULL DEFAULT 0 CHECK (structure_cost_percent BETWEEN 0 AND 100),
    system_cost_index_percent  numeric(10, 6) NOT NULL DEFAULT 0 CHECK (system_cost_index_percent BETWEEN 0 AND 100),
    tax_percent                numeric(7, 4) NOT NULL DEFAULT 0 CHECK (tax_percent BETWEEN 0 AND 100),
    scc_surcharge_percent      numeric(7, 4) NOT NULL DEFAULT 4 CHECK (scc_surcharge_percent BETWEEN 0 AND 100),
    created_at                 timestamptz   NOT NULL DEFAULT now()
);
CREATE INDEX facility_owner_ix ON facility (owner_account_id, name);
