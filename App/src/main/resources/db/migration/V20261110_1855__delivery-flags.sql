-- Items delivered to a capsuleer or to a corporation goal are waiting to be taken, like the ones in deliveries. They were
-- filed as ordinary hangar items because the flags were not recognised. `held` is worked out when the assets are stored,
-- so rows stored before the fix are corrected here, and later fetches store them right.
UPDATE character_asset   SET held = 'DELIVERIES' WHERE location_flag IN ('CapsuleerDeliveries', 'CorporationGoalDeliveries');
UPDATE corporation_asset SET held = 'DELIVERIES' WHERE location_flag IN ('CapsuleerDeliveries', 'CorporationGoalDeliveries');
