-- Blueprint ME, TE and runs are independent of asset inventory and follow the same replace-on-fetch rule.
CREATE TABLE character_blueprint (
    character_id       uuid        NOT NULL REFERENCES character (public_id) ON DELETE CASCADE,
    item_id            bigint      NOT NULL,
    type_id            integer     NOT NULL,
    location_id        bigint      NOT NULL,
    location_flag      text        NOT NULL,
    quantity           bigint      NOT NULL,
    material_efficiency smallint   NOT NULL,
    time_efficiency     smallint   NOT NULL,
    runs                integer    NOT NULL,
    PRIMARY KEY (character_id, item_id)
);
CREATE INDEX character_blueprint_type_ix ON character_blueprint (type_id);

CREATE TABLE corporation_blueprint (
    corporation_id     uuid        NOT NULL REFERENCES corporation (public_id) ON DELETE CASCADE,
    item_id            bigint      NOT NULL,
    type_id            integer     NOT NULL,
    location_id        bigint      NOT NULL,
    location_flag      text        NOT NULL,
    quantity           bigint      NOT NULL,
    material_efficiency smallint   NOT NULL,
    time_efficiency     smallint   NOT NULL,
    runs                integer    NOT NULL,
    PRIMARY KEY (corporation_id, item_id)
);
CREATE INDEX corporation_blueprint_type_ix ON corporation_blueprint (type_id);