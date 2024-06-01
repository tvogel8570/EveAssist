CREATE SEQUENCE IF NOT EXISTS eve_assist_user_id_seq START WITH 100 INCREMENT BY 1;

CREATE SEQUENCE IF NOT EXISTS pilot_id_seq START WITH 100 INCREMENT BY 1;

CREATE SEQUENCE IF NOT EXISTS scope_id_seq START WITH 100 INCREMENT BY 1;

CREATE SEQUENCE IF NOT EXISTS token_id_seq START WITH 100 INCREMENT BY 1;

CREATE TABLE eve_assist_user
(
    id               BIGINT                      NOT NULL,
    version          INTEGER,
    unique_user      UUID                        NOT NULL,
    email            VARCHAR(255)                NOT NULL,
    user_name        VARCHAR(50)                 NOT NULL,
    create_timestamp TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    update_timestamp TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT pk_eve_assist_user PRIMARY KEY (id)
);

CREATE TABLE pilot
(
    id                  BIGINT       NOT NULL,
    version             INTEGER,
    owner_hash          VARCHAR(255) NOT NULL,
    eve_pilot_id        BIGINT       NOT NULL,
    name                VARCHAR(255) NOT NULL,
    gender              VARCHAR(255),
    birthdate           TIMESTAMP WITHOUT TIME ZONE,
    portrait_url_tiny   VARCHAR(255),
    portrait_url_small  VARCHAR(255),
    portrait_url_medium VARCHAR(255),
    portrait_url_large  VARCHAR(255),
    security_status     DOUBLE PRECISION,
    title               VARCHAR(255),
    description         VARCHAR(255),
    alliance_id         INTEGER,
    alliance_desc       VARCHAR(255),
    faction_id          INTEGER,
    faction_desc        VARCHAR(255),
    corporation_id      INTEGER,
    corporation_desc    VARCHAR(255),
    race_id             INTEGER,
    race_desc           VARCHAR(255),
    bloodline_id        INTEGER,
    bloodline_desc      VARCHAR(255),
    modified            TIMESTAMP WITHOUT TIME ZONE,
    created             TIMESTAMP WITHOUT TIME ZONE,
    eve_assist_user_id  BIGINT,
    CONSTRAINT pk_pilot PRIMARY KEY (id)
);

CREATE TABLE scope
(
    id        BIGINT       NOT NULL,
    token_id  BIGINT,
    privilege VARCHAR(255) NOT NULL,
    CONSTRAINT pk_scope PRIMARY KEY (id)
);

CREATE TABLE token
(
    id            BIGINT NOT NULL,
    pilot_id      BIGINT,
    access_token  BYTEA  NOT NULL,
    refresh_token BYTEA  NOT NULL,
    CONSTRAINT pk_token PRIMARY KEY (id)
);

ALTER TABLE eve_assist_user
    ADD CONSTRAINT eve_assist_user_business_key UNIQUE (unique_user);

ALTER TABLE eve_assist_user
    ADD CONSTRAINT eve_assist_user_email_key UNIQUE (email);

ALTER TABLE eve_assist_user
    ADD CONSTRAINT eve_assist_user_user_name_key UNIQUE (user_name);

ALTER TABLE pilot
    ADD CONSTRAINT pilot_business_key UNIQUE (owner_hash, eve_pilot_id);

ALTER TABLE pilot
    ADD CONSTRAINT uc_pilot_name UNIQUE (name);

ALTER TABLE pilot
    ADD CONSTRAINT uc_pilot_owner_hash UNIQUE (owner_hash);

ALTER TABLE pilot
    ADD CONSTRAINT FK_PILOT_ON_EVE_ASSIST_USER FOREIGN KEY (eve_assist_user_id) REFERENCES eve_assist_user (id);

ALTER TABLE scope
    ADD CONSTRAINT FK_SCOPE_ON_TOKEN FOREIGN KEY (token_id) REFERENCES token (id);

ALTER TABLE token
    ADD CONSTRAINT FK_TOKEN_ON_PILOT FOREIGN KEY (pilot_id) REFERENCES pilot (id);