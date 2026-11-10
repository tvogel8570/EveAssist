-- ESI sends a blueprint original with ME and TE levels, is_blueprint_copy false and no runs at all, where its spec says
-- runs is -1. The flag from V20261109_1707 looked for -1, so no contract was flagged. An original is a blueprint line
-- (ME or TE present) that is not a copy.
UPDATE contract_public SET has_bpo = true
WHERE NOT has_bpo
  AND contract_id IN (SELECT contract_id FROM contract_public_item WHERE NOT is_copy AND (me IS NOT NULL OR te IS NOT NULL));
