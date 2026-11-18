-- A quote, and the costing of a quote, are never updated: the only way to change one is a new quote. The application has no update for
-- them, and this makes the database refuse one as well. (A migration that must convert stored documents to a newer format version
-- switches the trigger off for its own transaction with ALTER TABLE ... DISABLE TRIGGER, and on again after.)
CREATE FUNCTION refuse_frozen_update() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'a row of % is frozen and cannot be updated', TG_TABLE_NAME
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$;

CREATE TRIGGER project_quote_frozen BEFORE UPDATE ON project_quote
    FOR EACH ROW EXECUTE FUNCTION refuse_frozen_update();

-- Only the Draft's costing (no quote) is replaced.
CREATE TRIGGER project_costing_of_a_quote_frozen BEFORE UPDATE ON project_costing
    FOR EACH ROW WHEN (OLD.quote_id IS NOT NULL) EXECUTE FUNCTION refuse_frozen_update();
