CREATE TABLE sde.sde_version (name text NOT NULL, imported_at timestamptz NOT NULL DEFAULT now());
INSERT INTO sde.sde_version (name) VALUES ('test-sde');

CREATE TABLE sde."invCategories" ("categoryID" integer PRIMARY KEY, "categoryName" varchar(100));
INSERT INTO sde."invCategories" VALUES (4, 'Material'), (6, 'Ship'), (9, 'Blueprint'), (17, 'Commodity');

CREATE TABLE sde."invGroups" ("groupID" integer PRIMARY KEY, "categoryID" integer, "groupName" varchar(100));
INSERT INTO sde."invGroups" VALUES (1, 6, 'Ship'), (2, 17, 'Component'), (3, 4, 'Mineral'), (4, 9, 'Blueprint');

-- Where assets can be: real ids and names, a few of each, for the inventory page
CREATE TABLE sde."mapRegions" ("regionID" integer PRIMARY KEY, "regionName" varchar(100));
INSERT INTO sde."mapRegions" VALUES (10000002, 'The Forge'), (10000043, 'Domain');

CREATE TABLE sde."mapSolarSystems" ("solarSystemID" integer PRIMARY KEY, "regionID" integer, "solarSystemName" varchar(100));
INSERT INTO sde."mapSolarSystems" VALUES (30000142, 10000002, 'Jita'), (30002187, 10000043, 'Amarr');

CREATE TABLE sde."staStations" ("stationID" bigint PRIMARY KEY, "solarSystemID" integer, "regionID" integer,
    "stationName" varchar(100));
INSERT INTO sde."staStations" VALUES
    (60003760, 30000142, 10000002, 'Jita IV - Moon 4 - Caldari Navy Assembly Plant'),
    (60008494, 30002187, 10000043, 'Amarr VIII (Oris) - Emperor Family Academy');

CREATE TABLE sde."invTypes" ("typeID" integer PRIMARY KEY, "groupID" integer, "typeName" varchar(100), published boolean);
INSERT INTO sde."invTypes" VALUES
    (1001, 1, 'Test Hauler', true),
    (1002, 2, 'Test Component', true),
    (1003, 1, 'Second <b>Ship</b>', true),
    (1004, 2, 'Test Ammo', true),
    (1005, 1, 'Nested Ship', true),
    (1006, 2, 'Mid Part', true),
    (1007, 2, 'Deep Part', true),
    (34, 3, 'Tritanium', true),
    (35, 3, 'Pilot''s Mineral', true),
    (36, 3, 'Unpriced Ore', true),
    (2001, 4, 'Test Hauler Blueprint', true),
    (2002, 4, 'Test Component Blueprint', true),
    (2003, 4, 'Second Ship Blueprint', true),
    (2004, 4, 'Test Ammo Blueprint', true),
    (2005, 4, 'Nested Ship Blueprint', true),
    (2006, 4, 'Mid Part Blueprint', true),
    (2007, 4, 'Deep Part Blueprint', true);

CREATE TABLE sde."industryActivityProducts" ("typeID" integer, "activityID" integer, "productTypeID" integer, quantity integer);
INSERT INTO sde."industryActivityProducts" VALUES
    (2001, 1, 1001, 1), (2002, 1, 1002, 1), (2003, 1, 1003, 1), (2004, 1, 1004, 100),
    (2005, 1, 1005, 1), (2006, 1, 1006, 1), (2007, 1, 1007, 1);

-- Seconds a manufacturing run takes at TE 0
CREATE TABLE sde."industryActivity" ("typeID" integer, "activityID" integer, time integer);
INSERT INTO sde."industryActivity" VALUES
    (2001, 1, 6000), (2002, 1, 3000), (2003, 1, 600), (2004, 1, 1200),
    (2005, 1, 600), (2006, 1, 600), (2007, 1, 600);

CREATE TABLE sde."industryActivityMaterials" ("typeID" integer, "activityID" integer, "materialTypeID" integer, quantity integer);
INSERT INTO sde."industryActivityMaterials" VALUES
    (2001, 1, 1002, 2), (2001, 1, 34, 100),
    (2002, 1, 34, 10), (2002, 1, 35, 5),
    (2003, 1, 1002, 1), (2003, 1, 36, 1),
    (2004, 1, 34, 1),
    (2005, 1, 1006, 1), (2006, 1, 1007, 1), (2006, 1, 34, 1), (2007, 1, 34, 1);

-- A mutaplasmid (5001) that turns the Test Hauler's kind of item into type 3001, as in the real SDE
CREATE TABLE sde."dynamicItemAttributeMappings" ("typeID" integer, "applicableTypeID" integer, "resultingTypeID" integer);
INSERT INTO sde."dynamicItemAttributeMappings" VALUES (5001, 1002, 3001);
