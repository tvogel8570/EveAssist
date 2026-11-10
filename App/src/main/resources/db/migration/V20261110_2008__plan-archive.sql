-- A plan can be archived: it is hidden from the Plans list until the user asks to see archived plans, and can be restored.
-- Archiving is a status, not an edit, so it does not change the plan's version. Null means the plan is active.
ALTER TABLE plan ADD COLUMN archived_at timestamptz;
