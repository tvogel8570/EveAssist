-- The project's price basis gains the Jita split, the midpoint of the best buy and the best sell price
-- (documentation/requirements/market-logistics.md, "Price basis"). The column is text with a check, so the check is replaced.
ALTER TABLE project DROP CONSTRAINT project_price_side_check;
ALTER TABLE project ADD CONSTRAINT project_price_side_check CHECK (price_side IN ('sell', 'buy', 'split'));
