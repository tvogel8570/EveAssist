CREATE TABLE sde.sde_version (name text NOT NULL, imported_at timestamptz NOT NULL DEFAULT now());
INSERT INTO sde.sde_version (name) VALUES ('test-sde');

CREATE TABLE sde."invCategories" ("categoryID" integer PRIMARY KEY, "categoryName" varchar(100));
INSERT INTO sde."invCategories" VALUES (4, 'Material'), (6, 'Ship'), (9, 'Blueprint'), (17, 'Commodity');

CREATE TABLE sde."invGroups" ("groupID" integer PRIMARY KEY, "categoryID" integer, "groupName" varchar(100));
INSERT INTO sde."invGroups" VALUES (1, 6, 'Ship'), (2, 17, 'Component'), (3, 4, 'Mineral'), (4, 9, 'Blueprint'),
    (5, 16, 'Engineering');

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

CREATE TABLE sde."invTypes" ("typeID" integer PRIMARY KEY, "groupID" integer, "typeName" varchar(100), published boolean,
    volume double precision, "packagedVolume" double precision);
INSERT INTO sde."invTypes" VALUES
    (1001, 1, 'Test Hauler', true),
    (1002, 2, 'Test Component', true),
    (1003, 1, 'Second <b>Ship</b>', true),
    (1004, 2, 'Test Ammo', true),
    (1005, 1, 'Nested Ship', true),
    (1006, 2, 'Mid Part', true),
    (1007, 2, 'Deep Part', true),
    (1011, 1, 'Spare Hull A', true), (1012, 1, 'Spare Hull B', true), (1013, 1, 'Spare Hull C', true),
    (1014, 1, 'Spare Hull D', true),
    (34, 3, 'Tritanium', true),
    (35, 3, 'Pilot''s Mineral', true),
    (36, 3, 'Unpriced Ore', true),
    (2001, 4, 'Test Hauler Blueprint', true),
    (2002, 4, 'Test Component Blueprint', true),
    (2003, 4, 'Second Ship Blueprint', true),
    (2004, 4, 'Test Ammo Blueprint', true),
    (2005, 4, 'Nested Ship Blueprint', true),
    (2006, 4, 'Mid Part Blueprint', true),
    (2007, 4, 'Deep Part Blueprint', true),
    (2011, 4, 'Spare Hull A Blueprint', true), (2012, 4, 'Spare Hull B Blueprint', true),
    (2013, 4, 'Spare Hull C Blueprint', true), (2014, 4, 'Spare Hull D Blueprint', true),
    (3380, 5, 'Industry', true), (11442, 5, 'Nanite Engineering', true), (11452, 5, 'Mechanical Engineering', true);

-- Volumes in cubic meters: a ship is 5,000 assembled and 1,000 packaged, a component has no packaged volume, the ore is 0.01
UPDATE sde."invTypes" SET volume = 5000, "packagedVolume" = 1000 WHERE "typeID" = 1001;
UPDATE sde."invTypes" SET volume = 2 WHERE "typeID" = 1002;
UPDATE sde."invTypes" SET volume = 100 WHERE "typeID" = 1003;
UPDATE sde."invTypes" SET volume = 0.01 WHERE "typeID" IN (34, 35, 36);

CREATE TABLE sde."industryActivityProducts" ("typeID" integer, "activityID" integer, "productTypeID" integer, quantity integer);
INSERT INTO sde."industryActivityProducts" VALUES
    (2001, 1, 1001, 1), (2002, 1, 1002, 1), (2003, 1, 1003, 1), (2004, 1, 1004, 100),
    (2005, 1, 1005, 1), (2006, 1, 1006, 1), (2007, 1, 1007, 1),
    (2011, 1, 1011, 1), (2012, 1, 1012, 1), (2013, 1, 1013, 1), (2014, 1, 1014, 1);

-- Seconds a manufacturing run takes at TE 0
CREATE TABLE sde."industryActivity" ("typeID" integer, "activityID" integer, time integer);
INSERT INTO sde."industryActivity" VALUES
    (2001, 1, 6000), (2002, 1, 3000), (2003, 1, 600), (2004, 1, 1200),
    (2005, 1, 600), (2006, 1, 600), (2007, 1, 600),
    (2011, 1, 600), (2012, 1, 600), (2013, 1, 600), (2014, 1, 600);

CREATE TABLE sde."industryActivityMaterials" ("typeID" integer, "activityID" integer, "materialTypeID" integer, quantity integer);
INSERT INTO sde."industryActivityMaterials" VALUES
    (2001, 1, 1002, 2), (2001, 1, 34, 100),
    (2002, 1, 34, 10), (2002, 1, 35, 5),
    (2003, 1, 1002, 1), (2003, 1, 36, 1),
    (2004, 1, 34, 1),
    (2005, 1, 1006, 1), (2006, 1, 1007, 1), (2006, 1, 34, 1), (2007, 1, 34, 1),
    (2011, 1, 34, 1), (2012, 1, 34, 1), (2013, 1, 34, 1), (2014, 1, 34, 1);

-- A mutaplasmid (5001) that turns the Test Hauler's kind of item into type 3001, as in the real SDE
CREATE TABLE sde."dynamicItemAttributeMappings" ("typeID" integer, "applicableTypeID" integer, "resultingTypeID" integer);
INSERT INTO sde."dynamicItemAttributeMappings" VALUES (5001, 1002, 3001);

-- What tier a product is: the techLevel attribute (422), and the meta group for variants such as Faction or Structure.
-- 1001 is 1, 1002 has none (a component), 1003 is 2, 1004 has none but is a Structure Tech I item, 1005 is 3,
-- 1006 is a Faction item of level 1, and 1007 is a Storyline item of level 2 whose value is stored as a float.
CREATE TABLE sde."dgmTypeAttributes" ("typeID" integer NOT NULL, "attributeID" integer NOT NULL, "valueInt" integer,
    "valueFloat" double precision, PRIMARY KEY ("typeID", "attributeID"));
INSERT INTO sde."dgmTypeAttributes" VALUES
    (1001, 422, 1, NULL), (1003, 422, 2, NULL), (1005, 422, 3, NULL), (1006, 422, 1, NULL), (1007, 422, NULL, 2.0);

CREATE TABLE sde."invMetaGroups" ("metaGroupID" integer PRIMARY KEY, "metaGroupName" varchar(100));
INSERT INTO sde."invMetaGroups" VALUES (1, 'Tech I'), (2, 'Tech II'), (3, 'Storyline'), (4, 'Faction'), (14, 'Tech III'),
    (54, 'Structure Tech I');

CREATE TABLE sde."invMetaTypes" ("typeID" integer PRIMARY KEY, "parentTypeID" integer, "metaGroupID" integer);
INSERT INTO sde."invMetaTypes" VALUES (1004, NULL, 54), (1006, NULL, 4), (1007, NULL, 3);

-- The skills a blueprint requires for manufacturing (activity 1). Industry has no manufactureTimePerLevel, as in the real SDE.
-- Mechanical Engineering saves 2% a level and Nanite Engineering 1%, so they carry attribute 1982 (the values are negative).
CREATE TABLE sde."industryActivitySkills" ("typeID" integer, "activityID" integer, "skillID" integer, level integer);
INSERT INTO sde."industryActivitySkills" VALUES
    (2001, 1, 3380, 1), (2001, 1, 11452, 1), (2001, 1, 11442, 1),
    (2002, 1, 3380, 1),
    (2003, 1, 3380, 1), (2003, 1, 11442, 3);
INSERT INTO sde."dgmTypeAttributes" VALUES (11452, 1982, NULL, -2.0), (11442, 1982, NULL, -1.0);

