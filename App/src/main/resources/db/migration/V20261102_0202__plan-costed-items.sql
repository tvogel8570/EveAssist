-- The items a plan's stored total was priced from, so that a newer price marks only the plans that use it.
CREATE TABLE plan_costed_item (
    plan_id bigint  NOT NULL REFERENCES plan (id) ON DELETE CASCADE,
    type_id integer NOT NULL,
    PRIMARY KEY (plan_id, type_id)
);
CREATE INDEX plan_costed_item_type_ix ON plan_costed_item (type_id);
CREATE INDEX jita_price_as_of_ix ON jita_price (as_of);
