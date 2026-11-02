-- Parts the user chose to buy. Anything else with a blueprint is built.
CREATE TABLE plan_bought_part (
    plan_id bigint  NOT NULL REFERENCES plan (id) ON DELETE CASCADE,
    type_id integer NOT NULL,
    PRIMARY KEY (plan_id, type_id)
);
