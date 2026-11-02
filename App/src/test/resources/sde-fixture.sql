CREATE TABLE sde.sde_version (name text NOT NULL, imported_at timestamptz NOT NULL DEFAULT now());
INSERT INTO sde.sde_version (name) VALUES ('test-sde');

CREATE TABLE sde."invGroups" ("groupID" integer PRIMARY KEY, "groupName" varchar(100));
INSERT INTO sde."invGroups" VALUES (1, 'Ship'), (2, 'Component'), (3, 'Mineral'), (4, 'Blueprint');

CREATE TABLE sde."invTypes" ("typeID" integer PRIMARY KEY, "groupID" integer, "typeName" varchar(100), published boolean);
INSERT INTO sde."invTypes" VALUES
    (1001, 1, 'Test Hauler', true),
    (1002, 2, 'Test Component', true),
    (1003, 1, 'Second <b>Ship</b>', true),
    (1004, 2, 'Test Ammo', true),
    (34, 3, 'Tritanium', true),
    (35, 3, 'Pilot''s Mineral', true),
    (36, 3, 'Unpriced Ore', true),
    (2001, 4, 'Test Hauler Blueprint', true),
    (2002, 4, 'Test Component Blueprint', true),
    (2003, 4, 'Second Ship Blueprint', true),
    (2004, 4, 'Test Ammo Blueprint', true);

CREATE TABLE sde."industryActivityProducts" ("typeID" integer, "activityID" integer, "productTypeID" integer, quantity integer);
INSERT INTO sde."industryActivityProducts" VALUES
    (2001, 1, 1001, 1), (2002, 1, 1002, 1), (2003, 1, 1003, 1), (2004, 1, 1004, 100);

CREATE TABLE sde."industryActivityMaterials" ("typeID" integer, "activityID" integer, "materialTypeID" integer, quantity integer);
INSERT INTO sde."industryActivityMaterials" VALUES
    (2001, 1, 1002, 2), (2001, 1, 34, 100),
    (2002, 1, 34, 10), (2002, 1, 35, 5),
    (2003, 1, 1002, 1), (2003, 1, 36, 1),
    (2004, 1, 34, 1);
