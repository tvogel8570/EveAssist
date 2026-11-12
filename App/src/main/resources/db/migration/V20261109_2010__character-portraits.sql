-- The portrait is fetched once, when the character is linked, and served by EveAssist itself, because pages load
-- nothing from third parties (see gain-trust.md). It goes when the character goes.
ALTER TABLE character
    ADD COLUMN portrait            bytea,
    ADD COLUMN portrait_fetched_at timestamptz;
