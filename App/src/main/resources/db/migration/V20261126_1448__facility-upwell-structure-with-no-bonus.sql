-- Any Upwell structure can run manufacturing. Citadels and refineries give a job no bonus of their own, and are one structure type
-- (decided with the developer on 2026-11-26).
ALTER TABLE facility
    DROP CONSTRAINT facility_structure_type_check,
    ADD CONSTRAINT facility_structure_type_check
        CHECK (structure_type IN ('raitaru', 'azbel', 'sotiyo', 'npc_station', 'upwell_no_bonus'));
