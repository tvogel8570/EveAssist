-- What ESI said about the player structures where a character has items. ESI gives a structure's name only to a
-- character that may dock there, so the answer belongs to the character, goes when the character goes, and is never
-- shown to another account. A structure the character cannot see is recorded too, so that it is not asked about again
-- at once: each such answer would count toward ESI's error limit.
CREATE TABLE character_structure (
    character_id    uuid        NOT NULL REFERENCES character (public_id) ON DELETE CASCADE,
    structure_id    bigint      NOT NULL,
    -- FOUND, NO_ACCESS or NOT_FOUND
    status          text        NOT NULL CHECK (status IN ('FOUND', 'NO_ACCESS', 'NOT_FOUND')),
    name            text,
    solar_system_id integer,
    asked_at        timestamptz NOT NULL,
    PRIMARY KEY (character_id, structure_id),
    CHECK (status <> 'FOUND' OR (name IS NOT NULL AND solar_system_id IS NOT NULL))
);
