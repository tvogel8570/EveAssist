-- Slice 2 of the projects UI: the facility forms (documentation/requirements/projects.md, "Facilities").
--
-- structure_type says which structure the facility is. The form can fill the structure's own bonuses from it, and a page can name it. It is
-- optional, because the bonuses are typed and a facility the form has no type for (a Fortizar) just has none.
--
-- index_entered_at is when the cost index was last typed. The index is typed, not read from ESI, and the wormhole rule asks that its age be
-- shown. It moves only when the index changes, while updated_at moves when any value does. A facility that exists already gets the time it was
-- created, which is the latest the index could have been typed.
ALTER TABLE facility
    ADD COLUMN structure_type   text CHECK (structure_type IN ('raitaru', 'azbel', 'sotiyo', 'npc_station')),
    ADD COLUMN index_entered_at timestamptz NOT NULL DEFAULT now();

UPDATE facility SET index_entered_at = created_at;
