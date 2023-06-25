CREATE SEQUENCE IF NOT EXISTS pilot_seq START WITH 100 INCREMENT BY 1;

CREATE TABLE pilot
(
    id                  BIGINT       NOT NULL,
    version             INTEGER,
    owner_hash          VARCHAR(255) NOT NULL,
    pilot_id            BIGINT       NOT NULL,
    name                VARCHAR(255) NOT NULL,
    portrait_url_tiny   VARCHAR(255),
    portrait_url_small  VARCHAR(255),
    portrait_url_medium VARCHAR(255),
    portrait_url_large  VARCHAR(255),
    corporation_id      VARCHAR(255),
    alliance_id         VARCHAR(255),
    faction_id          VARCHAR(255),
    gender              VARCHAR(15),
    birthdate           TIMESTAMP WITHOUT TIME ZONE,
    modified            TIMESTAMP WITHOUT TIME ZONE,
    created             TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT pk_pilot PRIMARY KEY (id)
);

ALTER TABLE pilot
    ADD CONSTRAINT uc_owner_pilot UNIQUE (owner_hash, pilot_id);