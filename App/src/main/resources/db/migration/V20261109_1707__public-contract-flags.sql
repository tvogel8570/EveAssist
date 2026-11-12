-- Contracts with a blueprint original or an Abyssal item cannot be split into prices the way a pack of copies can, so
-- they are flagged and set aside (see documentation/requirements/market-logistics.md#contract-prices).
ALTER TABLE contract_public
    ADD COLUMN has_bpo     boolean NOT NULL DEFAULT false,
    ADD COLUMN has_abyssal boolean NOT NULL DEFAULT false;

-- A blueprint original has -1 runs. Abyssal items are recognised from the SDE in the app, so contracts read before
-- this change are not flagged for them; their Abyssal items have no Jita price, so they are skipped anyway.
UPDATE contract_public SET has_bpo = true
WHERE contract_id IN (SELECT contract_id FROM contract_public_item WHERE runs = -1 AND NOT is_copy);
