-- The builder's level in a time-saving skill is typed once for a project, not on each item (decided with the developer on 2026-11-25,
-- issue #64): one builder does all of a project's work, and a character assigned later replaces the list. Where two items of a project
-- held different levels of a skill, the higher one is kept.
CREATE TABLE project_skill (
    project_id    bigint   NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    skill_type_id integer  NOT NULL,
    level         smallint NOT NULL CHECK (level BETWEEN 0 AND 5),
    PRIMARY KEY (project_id, skill_type_id)
);

INSERT INTO project_skill (project_id, skill_type_id, level)
SELECT project_id, skill_type_id, max(level)
FROM project_item_skill
GROUP BY project_id, skill_type_id;

DROP TABLE project_item_skill;
