CREATE TABLE character (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id        uuid        NOT NULL UNIQUE,
    owner_account_id uuid        NOT NULL REFERENCES account (public_id) ON DELETE CASCADE,
    eve_character_id bigint      NOT NULL UNIQUE,
    name             text        NOT NULL,
    owner_hash       text        NOT NULL,
    scopes           text[]      NOT NULL,
    state            text        NOT NULL DEFAULT 'ACTIVE' CHECK (state IN ('ACTIVE', 'NEEDS_REAUTHORIZATION')),
    linked_at        timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX character_owner_ix ON character (owner_account_id);

-- Only refresh tokens are stored, encrypted in the app (see identity-and-tokens.md, Storing refresh tokens).
CREATE TABLE character_token (
    character_id bigint      PRIMARY KEY REFERENCES character (id) ON DELETE CASCADE,
    ciphertext   bytea       NOT NULL,
    nonce        bytea       NOT NULL CHECK (length(nonce) = 12),
    key_id       text        NOT NULL,
    updated_at   timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE account_feature (
    owner_account_id uuid        NOT NULL REFERENCES account (public_id) ON DELETE CASCADE,
    feature          text        NOT NULL,
    turned_on_at     timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (owner_account_id, feature)
);
