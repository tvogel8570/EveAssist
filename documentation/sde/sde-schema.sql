-- we don't know how to generate root <with-no-name> (class Root) :(

comment on database postgres is 'default administrative connection database';

grant connect, create, temporary on database keycloak to localdevuser;

create table evesde."acctEntryTypes"
(
    "entryTypeID"    integer not null
        primary key,
    "internalName"   varchar(100),
    name             varchar(100),
    "journalMessage" text
);

alter table evesde."acctEntryTypes"
    owner to yaml;

create table evesde."agtAgentTypes"
(
    "agentTypeID" integer not null
        primary key,
    "agentType"   varchar(50)
);

alter table evesde."agtAgentTypes"
    owner to yaml;

create table evesde."agtAgents"
(
    "agentID"       integer not null
        primary key,
    "divisionID"    integer,
    "corporationID" integer,
    "locationID"    integer,
    level           integer,
    quality         integer,
    "agentTypeID"   integer,
    "isLocator"     boolean
);

alter table evesde."agtAgents"
    owner to yaml;

create index "ix_evesde_agtAgents_corporationID"
    on evesde."agtAgents" ("corporationID");

create index "ix_evesde_agtAgents_locationID"
    on evesde."agtAgents" ("locationID");

create table evesde."agtAgentsInSpace"
(
    "agentID"       integer not null
        primary key,
    "dungeonID"     integer,
    "solarSystemID" integer,
    "spawnPointID"  integer,
    "typeID"        integer
);

alter table evesde."agtAgentsInSpace"
    owner to yaml;

create index "ix_evesde_agtAgentsInSpace_solarSystemID"
    on evesde."agtAgentsInSpace" ("solarSystemID");

create table evesde."agtResearchAgents"
(
    "agentID" integer not null,
    "typeID"  integer not null,
    primary key ("agentID", "typeID")
);

alter table evesde."agtResearchAgents"
    owner to yaml;

create index "ix_evesde_agtResearchAgents_typeID"
    on evesde."agtResearchAgents" ("typeID");

create table evesde."aplProximityEffectDbuffs"
(
    "typeID"  integer not null,
    "dbuffID" integer not null,
    value     double precision,
    primary key ("typeID", "dbuffID")
);

alter table evesde."aplProximityEffectDbuffs"
    owner to yaml;

create table evesde."aplProximityEffects"
(
    "typeID"       integer not null
        primary key,
    "delaySeconds" integer,
    radius         double precision
);

alter table evesde."aplProximityEffects"
    owner to yaml;

create table evesde."certCerts"
(
    "certID"    integer not null
        primary key,
    description text,
    "groupID"   integer,
    name        varchar(255)
);

alter table evesde."certCerts"
    owner to yaml;

create table evesde."certMasteries"
(
    "typeID"       integer,
    "masteryLevel" integer,
    "certID"       integer
);

alter table evesde."certMasteries"
    owner to yaml;

create table evesde."certRecommendedTypes"
(
    "certID" integer not null,
    "typeID" integer not null,
    primary key ("certID", "typeID")
);

alter table evesde."certRecommendedTypes"
    owner to yaml;

create table evesde."certSkills"
(
    "certID"        integer,
    "skillID"       integer,
    "certLevelInt"  integer,
    "skillLevel"    integer,
    "certLevelText" varchar(8)
);

alter table evesde."certSkills"
    owner to yaml;

create index "ix_evesde_certSkills_skillID"
    on evesde."certSkills" ("skillID");

create table evesde."chrAncestries"
(
    "ancestryID"       integer not null
        primary key,
    "ancestryName"     varchar(100),
    "bloodlineID"      integer,
    description        varchar(1000),
    perception         integer,
    willpower          integer,
    charisma           integer,
    memory             integer,
    intelligence       integer,
    "iconID"           integer,
    "shortDescription" varchar(500)
);

alter table evesde."chrAncestries"
    owner to yaml;

create table evesde."chrAttributes"
(
    "attributeID"      integer not null
        primary key,
    "attributeName"    varchar(100),
    description        varchar(1000),
    "iconID"           integer,
    "shortDescription" varchar(500),
    notes              varchar(500)
);

alter table evesde."chrAttributes"
    owner to yaml;

create table evesde."chrBloodlines"
(
    "bloodlineID"            integer not null
        primary key,
    "bloodlineName"          varchar(100),
    "raceID"                 integer,
    description              varchar(1000),
    "maleDescription"        varchar(1000),
    "femaleDescription"      varchar(1000),
    "shipTypeID"             integer,
    "corporationID"          integer,
    perception               integer,
    willpower                integer,
    charisma                 integer,
    memory                   integer,
    intelligence             integer,
    "iconID"                 integer,
    "shortDescription"       varchar(500),
    "shortMaleDescription"   varchar(500),
    "shortFemaleDescription" varchar(500)
);

alter table evesde."chrBloodlines"
    owner to yaml;

create table evesde."chrCloneGradeSkills"
(
    "cloneGradeID" integer not null,
    "typeID"       integer not null,
    level          integer,
    primary key ("cloneGradeID", "typeID")
);

alter table evesde."chrCloneGradeSkills"
    owner to yaml;

create table evesde."chrCloneGrades"
(
    "cloneGradeID" serial
        primary key,
    name           varchar(100)
);

alter table evesde."chrCloneGrades"
    owner to yaml;

create table evesde."chrFactions"
(
    "factionID"            integer not null
        primary key,
    "factionName"          varchar(100),
    description            varchar(2000),
    "raceIDs"              integer,
    "solarSystemID"        integer,
    "corporationID"        integer,
    "sizeFactor"           double precision,
    "stationCount"         integer,
    "stationSystemCount"   integer,
    "militiaCorporationID" integer,
    "iconID"               integer
);

alter table evesde."chrFactions"
    owner to yaml;

create table evesde."chrRaces"
(
    "raceID"           integer not null
        primary key,
    "raceName"         varchar(100),
    description        varchar(1000),
    "iconID"           integer,
    "shortDescription" varchar(500)
);

alter table evesde."chrRaces"
    owner to yaml;

create table evesde."chrSchoolCareerAgents"
(
    "schoolID"    integer not null,
    "characterID" integer not null,
    primary key ("schoolID", "characterID")
);

alter table evesde."chrSchoolCareerAgents"
    owner to yaml;

create table evesde."chrSchoolMap"
(
    id              integer not null
        primary key,
    "schoolID"      integer,
    "solarSystemID" integer
);

alter table evesde."chrSchoolMap"
    owner to yaml;

create table evesde."chrSchoolStartingStations"
(
    "schoolID"  integer not null,
    "stationID" integer not null,
    primary key ("schoolID", "stationID")
);

alter table evesde."chrSchoolStartingStations"
    owner to yaml;

create table evesde."chrSchools"
(
    "schoolID"             integer not null
        primary key,
    "careerID"             integer,
    "corporationID"        integer,
    "iconID"               integer,
    "raceID"               integer,
    name                   varchar(100),
    description            text,
    title                  varchar(200),
    "characterDescription" text
);

alter table evesde."chrSchools"
    owner to yaml;

create table evesde."chrTitles"
(
    "titleID"   varchar(36) not null
        primary key,
    "titleName" varchar(255)
);

alter table evesde."chrTitles"
    owner to yaml;

create table evesde."compressibleTypes"
(
    "typeID"           integer not null
        primary key,
    "compressedTypeID" integer not null
);

alter table evesde."compressibleTypes"
    owner to yaml;

create table evesde."crpActivities"
(
    "activityID"   integer not null
        primary key,
    "activityName" varchar(100),
    description    varchar(1000)
);

alter table evesde."crpActivities"
    owner to yaml;

create table evesde."crpNPCCorporationAllowedRaces"
(
    "corporationID" integer not null,
    "raceID"        integer not null,
    primary key ("corporationID", "raceID")
);

alter table evesde."crpNPCCorporationAllowedRaces"
    owner to yaml;

create table evesde."crpNPCCorporationDivisions"
(
    "corporationID"  integer not null,
    "divisionID"     integer not null,
    size             integer,
    "divisionNumber" integer,
    "leaderID"       integer,
    primary key ("corporationID", "divisionID")
);

alter table evesde."crpNPCCorporationDivisions"
    owner to yaml;

create table evesde."crpNPCCorporationExchangeRates"
(
    "corporationID"   integer not null,
    "toCorporationID" integer not null,
    rate              double precision,
    primary key ("corporationID", "toCorporationID")
);

alter table evesde."crpNPCCorporationExchangeRates"
    owner to yaml;

create table evesde."crpNPCCorporationLPOfferTables"
(
    "corporationID"  integer not null,
    "lpOfferTableID" integer not null,
    primary key ("corporationID", "lpOfferTableID")
);

alter table evesde."crpNPCCorporationLPOfferTables"
    owner to yaml;

create table evesde."crpNPCCorporationResearchFields"
(
    "skillID"       integer not null,
    "corporationID" integer not null,
    primary key ("skillID", "corporationID")
);

alter table evesde."crpNPCCorporationResearchFields"
    owner to yaml;

create table evesde."crpNPCCorporationTrades"
(
    "corporationID" integer not null,
    "typeID"        integer not null,
    value           double precision,
    primary key ("corporationID", "typeID")
);

alter table evesde."crpNPCCorporationTrades"
    owner to yaml;

create table evesde."crpNPCCorporations"
(
    "corporationID"       integer not null
        primary key,
    size                  char,
    extent                char,
    "solarSystemID"       integer,
    "investorID1"         integer,
    "investorShares1"     integer,
    "investorID2"         integer,
    "investorShares2"     integer,
    "investorID3"         integer,
    "investorShares3"     integer,
    "investorID4"         integer,
    "investorShares4"     integer,
    "friendID"            integer,
    "enemyID"             integer,
    "publicShares"        bigint,
    "initialPrice"        integer,
    "minSecurity"         double precision,
    scattered             boolean,
    fringe                integer,
    corridor              integer,
    hub                   integer,
    border                integer,
    "factionID"           integer,
    "sizeFactor"          double precision,
    "stationCount"        integer,
    "stationSystemCount"  integer,
    description           varchar(4000),
    "iconID"              integer,
    "corporationName"     varchar(100),
    "tickerName"          varchar(10),
    "ceoID"               integer,
    "mainActivityID"      integer,
    "secondaryActivityID" integer,
    "taxRate"             double precision
);

alter table evesde."crpNPCCorporations"
    owner to yaml;

create table evesde."crpNPCDivisions"
(
    "divisionID"   integer not null
        primary key,
    "divisionName" varchar(100),
    description    varchar(1000),
    "leaderType"   varchar(100)
);

alter table evesde."crpNPCDivisions"
    owner to yaml;

create table evesde."crpRoleGroups"
(
    "roleGroupID"        integer not null
        primary key,
    "appliesTo"          varchar(50),
    "appliesToGrantable" varchar(50),
    "isDivisional"       boolean,
    "isLocational"       boolean,
    name                 varchar(100)
);

alter table evesde."crpRoleGroups"
    owner to yaml;

create table evesde."crpRoleRoleGroups"
(
    "roleID"      integer not null,
    "roleGroupID" integer not null,
    primary key ("roleID", "roleGroupID")
);

alter table evesde."crpRoleRoleGroups"
    owner to yaml;

create table evesde."crpRoles"
(
    "roleID"    integer not null
        primary key,
    "shortName" varchar(100),
    name        varchar(100),
    description text
);

alter table evesde."crpRoles"
    owner to yaml;

create table evesde."dbuffCollections"
(
    "dbuffID"              integer not null
        primary key,
    "displayName"          varchar(255),
    "developerDescription" varchar(255),
    "aggregateMode"        varchar(20),
    "operationName"        varchar(30),
    "showOutputValueInUI"  varchar(20)
);

alter table evesde."dbuffCollections"
    owner to yaml;

create table evesde."dbuffItemModifiers"
(
    "dbuffID"          integer not null,
    "dogmaAttributeID" integer not null,
    primary key ("dbuffID", "dogmaAttributeID")
);

alter table evesde."dbuffItemModifiers"
    owner to yaml;

create table evesde."dbuffLocationGroupModifiers"
(
    "dbuffID"          integer not null,
    "dogmaAttributeID" integer not null,
    "groupID"          integer not null,
    primary key ("dbuffID", "dogmaAttributeID", "groupID")
);

alter table evesde."dbuffLocationGroupModifiers"
    owner to yaml;

create table evesde."dbuffLocationModifiers"
(
    "dbuffID"          integer not null,
    "dogmaAttributeID" integer not null,
    primary key ("dbuffID", "dogmaAttributeID")
);

alter table evesde."dbuffLocationModifiers"
    owner to yaml;

create table evesde."dbuffLocationRequiredSkillModifiers"
(
    "dbuffID"          integer not null,
    "dogmaAttributeID" integer not null,
    "skillID"          integer not null,
    primary key ("dbuffID", "dogmaAttributeID", "skillID")
);

alter table evesde."dbuffLocationRequiredSkillModifiers"
    owner to yaml;

create table evesde."dgmAttributeCategories"
(
    "categoryID"          integer not null
        primary key,
    "categoryName"        varchar(50),
    "categoryDescription" varchar(200)
);

alter table evesde."dgmAttributeCategories"
    owner to yaml;

create table evesde."dgmAttributeTypes"
(
    "attributeID"          integer not null
        primary key,
    "attributeName"        varchar(100),
    description            varchar(1000),
    "iconID"               integer,
    "defaultValue"         double precision,
    published              boolean,
    "displayName"          varchar(150),
    "unitID"               integer,
    stackable              boolean,
    "highIsGood"           boolean,
    "categoryID"           integer,
    "maxAttributeID"       integer,
    "minAttributeID"       integer,
    "chargeRechargeTimeID" integer,
    "dataType"             integer,
    "displayWhenZero"      boolean,
    "tooltipTitle"         varchar(255),
    "tooltipDescription"   text
);

alter table evesde."dgmAttributeTypes"
    owner to yaml;

create table evesde."dgmEffects"
(
    "effectID"                       integer not null
        primary key,
    "effectName"                     varchar(400),
    "effectCategory"                 integer,
    "preExpression"                  integer,
    "postExpression"                 integer,
    description                      varchar(1000),
    guid                             varchar(60),
    "iconID"                         integer,
    "isOffensive"                    boolean,
    "isAssistance"                   boolean,
    "durationAttributeID"            integer,
    "trackingSpeedAttributeID"       integer,
    "dischargeAttributeID"           integer,
    "rangeAttributeID"               integer,
    "falloffAttributeID"             integer,
    "disallowAutoRepeat"             boolean,
    published                        boolean,
    "displayName"                    varchar(100),
    "isWarpSafe"                     boolean,
    "rangeChance"                    boolean,
    "electronicChance"               boolean,
    "propulsionChance"               boolean,
    distribution                     integer,
    "sfxName"                        varchar(20),
    "npcUsageChanceAttributeID"      integer,
    "npcActivationChanceAttributeID" integer,
    "fittingUsageChanceAttributeID"  integer,
    "modifierInfo"                   text,
    "resistanceAttributeID"          integer
);

alter table evesde."dgmEffects"
    owner to yaml;

create table evesde."dgmExpressions"
(
    "expressionID"          integer not null
        primary key,
    "operandID"             integer,
    arg1                    integer,
    arg2                    integer,
    "expressionValue"       varchar(100),
    description             varchar(1000),
    "expressionName"        varchar(500),
    "expressionTypeID"      integer,
    "expressionGroupID"     integer,
    "expressionAttributeID" integer
);

alter table evesde."dgmExpressions"
    owner to yaml;

create table evesde."dgmTypeAttributes"
(
    "typeID"      integer not null,
    "attributeID" integer not null,
    "valueInt"    integer,
    "valueFloat"  double precision,
    primary key ("typeID", "attributeID")
);

alter table evesde."dgmTypeAttributes"
    owner to yaml;

create index "ix_evesde_dgmTypeAttributes_attributeID"
    on evesde."dgmTypeAttributes" ("attributeID");

create table evesde."dgmTypeEffects"
(
    "typeID"    integer not null,
    "effectID"  integer not null,
    "isDefault" boolean,
    primary key ("typeID", "effectID")
);

alter table evesde."dgmTypeEffects"
    owner to yaml;

create table evesde."dungeonAllowedShips"
(
    "dungeonID" integer not null,
    "typeID"    integer not null,
    primary key ("dungeonID", "typeID")
);

alter table evesde."dungeonAllowedShips"
    owner to yaml;

create table evesde."dungeonArchetypes"
(
    "archetypeID" integer not null
        primary key,
    title         varchar(255),
    description   text
);

alter table evesde."dungeonArchetypes"
    owner to yaml;

create table evesde.dungeons
(
    "dungeonID"   integer not null
        primary key,
    "dungeonName" varchar(255),
    description   text,
    "archetypeID" integer,
    "factionID"   integer
);

alter table evesde.dungeons
    owner to yaml;

create table evesde."dynamicItemAttributeMappings"
(
    "typeID"           integer not null,
    "applicableTypeID" integer not null,
    "resultingTypeID"  integer,
    primary key ("typeID", "applicableTypeID")
);

alter table evesde."dynamicItemAttributeMappings"
    owner to yaml;

create table evesde."dynamicItemAttributes"
(
    "typeID"      integer not null,
    "attributeID" integer not null,
    min           double precision,
    max           double precision,
    "highIsGood"  boolean,
    primary key ("typeID", "attributeID")
);

alter table evesde."dynamicItemAttributes"
    owner to yaml;

create table evesde."epicArcMissionNextMissions"
(
    "missionID"     integer not null,
    "nextMissionID" integer not null,
    primary key ("missionID", "nextMissionID")
);

alter table evesde."epicArcMissionNextMissions"
    owner to yaml;

create table evesde."epicArcMissions"
(
    "arcID"         integer not null,
    "missionID"     integer not null,
    "agentID"       integer,
    "failMissionID" integer,
    primary key ("arcID", "missionID")
);

alter table evesde."epicArcMissions"
    owner to yaml;

create table evesde."epicArcs"
(
    "arcID"              integer not null
        primary key,
    "arcName"            varchar(255),
    "factionID"          integer,
    "iconID"             integer,
    "arcRestartInterval" integer
);

alter table evesde."epicArcs"
    owner to yaml;

create table evesde."eveGraphics"
(
    "graphicID"        integer not null
        primary key,
    "sofFactionName"   varchar(100),
    "graphicFile"      varchar(256),
    "sofHullName"      varchar(100),
    "sofRaceName"      varchar(100),
    description        text,
    "iconFolder"       varchar(256),
    "sofMaterialSetID" integer
);

alter table evesde."eveGraphics"
    owner to yaml;

create table evesde."eveIcons"
(
    "iconID"    integer not null
        primary key,
    "iconFile"  varchar(500),
    description text
);

alter table evesde."eveIcons"
    owner to yaml;

create table evesde."eveUnits"
(
    "unitID"      integer not null
        primary key,
    "unitName"    varchar(100),
    "displayName" varchar(50),
    description   varchar(1000)
);

alter table evesde."eveUnits"
    owner to yaml;

create table evesde."expertSystemShipTypes"
(
    "typeID"     integer not null,
    "shipTypeID" integer not null,
    primary key ("typeID", "shipTypeID")
);

alter table evesde."expertSystemShipTypes"
    owner to yaml;

create table evesde."expertSystemSkillsGranted"
(
    "typeID"      integer not null,
    "skillTypeID" integer not null,
    level         integer,
    primary key ("typeID", "skillTypeID")
);

alter table evesde."expertSystemSkillsGranted"
    owner to yaml;

create table evesde."expertSystems"
(
    "typeID"       integer not null
        primary key,
    "durationDays" integer,
    hidden         boolean,
    "internalName" varchar(200),
    retired        boolean
);

alter table evesde."expertSystems"
    owner to yaml;

create table evesde."fighterAbilities"
(
    "abilityID"         integer not null
        primary key,
    "disallowInHighSec" boolean,
    "disallowInLowSec"  boolean,
    "displayName"       varchar(200),
    "iconID"            integer,
    "targetMode"        varchar(50),
    "tooltipText"       text
);

alter table evesde."fighterAbilities"
    owner to yaml;

create table evesde."fighterAbilitiesByType"
(
    "typeID"           integer not null,
    "slotNumber"       integer not null,
    "abilityID"        integer,
    "cooldownSeconds"  integer,
    "chargeCount"      integer,
    "rearmTimeSeconds" integer,
    primary key ("typeID", "slotNumber")
);

alter table evesde."fighterAbilitiesByType"
    owner to yaml;

create table evesde."freelanceJobSchemaContentTags"
(
    "jobSchemaID" varchar(100) not null,
    tag           varchar(100) not null,
    primary key ("jobSchemaID", tag)
);

alter table evesde."freelanceJobSchemaContentTags"
    owner to yaml;

create table evesde."freelanceJobSchemaParameters"
(
    "jobSchemaID"        varchar(100) not null,
    "paramKey"           varchar(100) not null,
    "acceptedValueTypes" text,
    primary key ("jobSchemaID", "paramKey")
);

alter table evesde."freelanceJobSchemaParameters"
    owner to yaml;

create table evesde."freelanceJobSchemas"
(
    "jobSchemaID"         varchar(100) not null
        primary key,
    title                 varchar(255),
    description           text,
    "progressDescription" text,
    "rewardDescription"   text,
    "targetDescription"   text,
    "iconID"              varchar(100)
);

alter table evesde."freelanceJobSchemas"
    owner to yaml;

create table evesde."graphicMaterialSets"
(
    "materialSetID"   integer not null
        primary key,
    description       varchar(256),
    "sofFactionName"  varchar(100),
    "sofRaceHint"     varchar(100),
    "colorHullR"      double precision,
    "colorHullG"      double precision,
    "colorHullB"      double precision,
    "colorHullA"      double precision,
    "colorPrimaryR"   double precision,
    "colorPrimaryG"   double precision,
    "colorPrimaryB"   double precision,
    "colorPrimaryA"   double precision,
    "colorSecondaryR" double precision,
    "colorSecondaryG" double precision,
    "colorSecondaryB" double precision,
    "colorSecondaryA" double precision,
    "colorWindowR"    double precision,
    "colorWindowG"    double precision,
    "colorWindowB"    double precision,
    "colorWindowA"    double precision
);

alter table evesde."graphicMaterialSets"
    owner to yaml;

create table evesde."indModifierSources"
(
    "typeID"           integer     not null,
    "activityName"     varchar(50) not null,
    "modifierType"     varchar(20) not null,
    "dogmaAttributeID" integer     not null,
    "filterID"         integer,
    primary key ("typeID", "activityName", "modifierType", "dogmaAttributeID")
);

alter table evesde."indModifierSources"
    owner to yaml;

create table evesde."indTargetFilterCategories"
(
    "targetFilterID" integer not null,
    "categoryID"     integer not null,
    primary key ("targetFilterID", "categoryID")
);

alter table evesde."indTargetFilterCategories"
    owner to yaml;

create table evesde."indTargetFilterGroups"
(
    "targetFilterID" integer not null,
    "groupID"        integer not null,
    primary key ("targetFilterID", "groupID")
);

alter table evesde."indTargetFilterGroups"
    owner to yaml;

create table evesde."indTargetFilters"
(
    "targetFilterID" integer not null
        primary key,
    name             varchar(100)
);

alter table evesde."indTargetFilters"
    owner to yaml;

create table evesde."industryActivity"
(
    "typeID"     integer not null,
    "activityID" integer not null,
    time         integer,
    primary key ("typeID", "activityID")
);

alter table evesde."industryActivity"
    owner to yaml;

create index "ix_evesde_industryActivity_activityID"
    on evesde."industryActivity" ("activityID");

create table evesde."industryActivityMaterials"
(
    "typeID"         integer,
    "activityID"     integer,
    "materialTypeID" integer,
    quantity         integer
);

alter table evesde."industryActivityMaterials"
    owner to yaml;

create index "industryActivityMaterials_idx1"
    on evesde."industryActivityMaterials" ("typeID", "activityID");

create index "ix_evesde_industryActivityMaterials_typeID"
    on evesde."industryActivityMaterials" ("typeID");

create table evesde."industryActivityProbabilities"
(
    "typeID"        integer,
    "activityID"    integer,
    "productTypeID" integer,
    probability     numeric(3, 2)
);

alter table evesde."industryActivityProbabilities"
    owner to yaml;

create index "ix_evesde_industryActivityProbabilities_productTypeID"
    on evesde."industryActivityProbabilities" ("productTypeID");

create index "ix_evesde_industryActivityProbabilities_typeID"
    on evesde."industryActivityProbabilities" ("typeID");

create table evesde."industryActivityProducts"
(
    "typeID"        integer,
    "activityID"    integer,
    "productTypeID" integer,
    quantity        integer
);

alter table evesde."industryActivityProducts"
    owner to yaml;

create index "ix_evesde_industryActivityProducts_productTypeID"
    on evesde."industryActivityProducts" ("productTypeID");

create index "ix_evesde_industryActivityProducts_typeID"
    on evesde."industryActivityProducts" ("typeID");

create table evesde."industryActivityRaces"
(
    "typeID"        integer,
    "activityID"    integer,
    "productTypeID" integer,
    "raceID"        integer
);

alter table evesde."industryActivityRaces"
    owner to yaml;

create index "ix_evesde_industryActivityRaces_productTypeID"
    on evesde."industryActivityRaces" ("productTypeID");

create index "ix_evesde_industryActivityRaces_typeID"
    on evesde."industryActivityRaces" ("typeID");

create table evesde."industryActivitySkills"
(
    "typeID"     integer,
    "activityID" integer,
    "skillID"    integer,
    level        integer
);

alter table evesde."industryActivitySkills"
    owner to yaml;

create index "industryActivitySkills_idx1"
    on evesde."industryActivitySkills" ("typeID", "activityID");

create index "ix_evesde_industryActivitySkills_skillID"
    on evesde."industryActivitySkills" ("skillID");

create index "ix_evesde_industryActivitySkills_typeID"
    on evesde."industryActivitySkills" ("typeID");

create table evesde."industryBlueprints"
(
    "typeID"             integer not null
        primary key,
    "maxProductionLimit" integer
);

alter table evesde."industryBlueprints"
    owner to yaml;

create table evesde."invCategories"
(
    "categoryID"   integer not null
        primary key,
    "categoryName" varchar(100),
    "iconID"       integer,
    published      boolean
);

alter table evesde."invCategories"
    owner to yaml;

create table evesde."invContrabandTypes"
(
    "factionID"        integer not null,
    "typeID"           integer not null,
    "standingLoss"     double precision,
    "confiscateMinSec" double precision,
    "fineByValue"      double precision,
    "attackMinSec"     double precision,
    primary key ("factionID", "typeID")
);

alter table evesde."invContrabandTypes"
    owner to yaml;

create index "ix_evesde_invContrabandTypes_typeID"
    on evesde."invContrabandTypes" ("typeID");

create table evesde."invControlTowerResourcePurposes"
(
    purpose       integer not null
        primary key,
    "purposeText" varchar(100)
);

alter table evesde."invControlTowerResourcePurposes"
    owner to yaml;

create table evesde."invControlTowerResources"
(
    "controlTowerTypeID" integer not null,
    "resourceTypeID"     integer not null,
    purpose              integer,
    quantity             integer,
    "minSecurityLevel"   double precision,
    "factionID"          integer,
    primary key ("controlTowerTypeID", "resourceTypeID")
);

alter table evesde."invControlTowerResources"
    owner to yaml;

create table evesde."invFlags"
(
    "flagID"   integer not null
        primary key,
    "flagName" varchar(200),
    "flagText" varchar(100),
    "orderID"  integer
);

alter table evesde."invFlags"
    owner to yaml;

create table evesde."invGroups"
(
    "groupID"              integer not null
        primary key,
    "categoryID"           integer,
    "groupName"            varchar(100),
    "iconID"               integer,
    "useBasePrice"         boolean,
    anchored               boolean,
    anchorable             boolean,
    "fittableNonSingleton" boolean,
    published              boolean
);

alter table evesde."invGroups"
    owner to yaml;

create index "ix_evesde_invGroups_categoryID"
    on evesde."invGroups" ("categoryID");

create table evesde."invItems"
(
    "itemID"     integer not null
        primary key,
    "typeID"     integer not null,
    "ownerID"    integer not null,
    "locationID" integer not null,
    "flagID"     integer not null,
    quantity     integer not null
);

alter table evesde."invItems"
    owner to yaml;

create index "items_IX_OwnerLocation"
    on evesde."invItems" ("ownerID", "locationID");

create index "ix_evesde_invItems_locationID"
    on evesde."invItems" ("locationID");

create table evesde."invMarketGroups"
(
    "marketGroupID"   integer not null
        primary key,
    "parentGroupID"   integer,
    "marketGroupName" varchar(100),
    description       varchar(3000),
    "iconID"          integer,
    "hasTypes"        boolean
);

alter table evesde."invMarketGroups"
    owner to yaml;

create table evesde."invMetaGroups"
(
    "metaGroupID"   integer not null
        primary key,
    "metaGroupName" varchar(100),
    description     varchar(1000),
    "iconID"        integer
);

alter table evesde."invMetaGroups"
    owner to yaml;

create table evesde."invMetaTypes"
(
    "typeID"       integer not null
        primary key,
    "parentTypeID" integer,
    "metaGroupID"  integer
);

alter table evesde."invMetaTypes"
    owner to yaml;

create table evesde."invNames"
(
    "itemID"   integer      not null
        primary key,
    "itemName" varchar(200) not null
);

alter table evesde."invNames"
    owner to yaml;

create table evesde."invPositions"
(
    "itemID" integer          not null
        primary key,
    x        double precision not null,
    y        double precision not null,
    z        double precision not null,
    yaw      real,
    pitch    real,
    roll     real
);

alter table evesde."invPositions"
    owner to yaml;

create table evesde."invTraits"
(
    "traitID"    serial
        primary key,
    "typeID"     integer,
    "skillID"    integer,
    bonus        double precision,
    "bonusText"  text,
    "unitID"     integer,
    importance   integer,
    "isPositive" boolean
);

alter table evesde."invTraits"
    owner to yaml;

create table evesde."invTypeMaterials"
(
    "typeID"         integer not null,
    "materialTypeID" integer not null,
    quantity         integer not null,
    primary key ("typeID", "materialTypeID")
);

alter table evesde."invTypeMaterials"
    owner to yaml;

create table evesde."invTypeRandomizedMaterials"
(
    "typeID"         integer not null,
    "materialTypeID" integer not null,
    "quantityMin"    integer,
    "quantityMax"    integer,
    primary key ("typeID", "materialTypeID")
);

alter table evesde."invTypeRandomizedMaterials"
    owner to yaml;

create table evesde."invTypeReactions"
(
    "reactionTypeID" integer not null,
    input            boolean not null,
    "typeID"         integer not null,
    quantity         integer,
    primary key ("reactionTypeID", input, "typeID")
);

alter table evesde."invTypeReactions"
    owner to yaml;

create table evesde."invTypes"
(
    "typeID"          integer not null
        primary key,
    "groupID"         integer,
    "typeName"        varchar(100),
    description       text,
    mass              double precision,
    volume            double precision,
    capacity          double precision,
    "portionSize"     integer,
    "raceID"          integer,
    "basePrice"       numeric(19, 4),
    published         boolean,
    "marketGroupID"   integer,
    "iconID"          integer,
    "soundID"         integer,
    "graphicID"       integer,
    "factionID"       integer,
    "metaLevel"       integer,
    "techLevel"       integer,
    "shipTreeGroupID" integer,
    "packagedVolume"  double precision,
    "isDynamicType"   boolean,
    "isRepackable"    boolean
);

alter table evesde."invTypes"
    owner to yaml;

create index "ix_evesde_invTypes_groupID"
    on evesde."invTypes" ("groupID");

create table evesde."invUniqueNames"
(
    "itemID"   integer      not null
        primary key,
    "itemName" varchar(200) not null,
    "groupID"  integer
);

alter table evesde."invUniqueNames"
    owner to yaml;

create index "invUniqueNames_IX_GroupName"
    on evesde."invUniqueNames" ("groupID", "itemName");

create unique index "ix_evesde_invUniqueNames_itemName"
    on evesde."invUniqueNames" ("itemName");

create table evesde."invVolumes"
(
    "typeID" integer not null
        primary key,
    volume   integer
);

alter table evesde."invVolumes"
    owner to yaml;

create table evesde."linkWithShip"
(
    "typeID"                       integer not null
        primary key,
    "applyPvpFlag"                 boolean,
    "canRelink"                    boolean,
    "characterEnergyCost"          double precision,
    "dbuffPostLinkDuration"        integer,
    "generateCynoInhibitor"        boolean,
    "keepDbuffDurationOnLinkBreak" boolean,
    "linkDuration"                 integer,
    "linkEffectGraphicIDOverride"  integer,
    "linkableShipTypeListID"       integer,
    "maxLinkRange"                 double precision,
    "omegaOnly"                    boolean,
    "solarsystemInterferenceCost"  double precision
);

alter table evesde."linkWithShip"
    owner to yaml;

create table evesde."linkWithShipDbuffs"
(
    "typeID"  integer not null,
    "dbuffID" integer not null,
    value     double precision,
    primary key ("typeID", "dbuffID")
);

alter table evesde."linkWithShipDbuffs"
    owner to yaml;

create table evesde."mapCelestialGraphics"
(
    "celestialID"  integer not null
        primary key,
    "heightMap1"   integer,
    "heightMap2"   integer,
    "shaderPreset" integer,
    population     boolean
);

alter table evesde."mapCelestialGraphics"
    owner to yaml;

create table evesde."mapCelestialStatistics"
(
    "celestialID"    integer not null
        primary key,
    temperature      double precision,
    "spectralClass"  varchar(10),
    luminosity       double precision,
    age              double precision,
    life             double precision,
    "orbitRadius"    double precision,
    eccentricity     double precision,
    "massDust"       double precision,
    "massGas"        double precision,
    fragmented       boolean,
    density          double precision,
    "surfaceGravity" double precision,
    "escapeVelocity" double precision,
    "orbitPeriod"    double precision,
    "rotationRate"   double precision,
    locked           boolean,
    pressure         double precision,
    radius           double precision,
    mass             integer
);

alter table evesde."mapCelestialStatistics"
    owner to yaml;

create table evesde."mapConstellationJumps"
(
    "fromRegionID"        integer,
    "fromConstellationID" integer not null,
    "toConstellationID"   integer not null,
    "toRegionID"          integer,
    primary key ("fromConstellationID", "toConstellationID")
);

alter table evesde."mapConstellationJumps"
    owner to yaml;

create table evesde."mapConstellations"
(
    "regionID"          integer,
    "constellationID"   integer not null
        primary key,
    "constellationName" varchar(100),
    x                   double precision,
    y                   double precision,
    z                   double precision,
    "xMin"              double precision,
    "xMax"              double precision,
    "yMin"              double precision,
    "yMax"              double precision,
    "zMin"              double precision,
    "zMax"              double precision,
    "factionID"         integer,
    radius              double precision
);

alter table evesde."mapConstellations"
    owner to yaml;

create table evesde."mapDenormalize"
(
    "itemID"          integer not null
        primary key,
    "typeID"          integer,
    "groupID"         integer,
    "solarSystemID"   integer,
    "constellationID" integer,
    "regionID"        integer,
    "orbitID"         integer,
    x                 double precision,
    y                 double precision,
    z                 double precision,
    radius            double precision,
    "itemName"        varchar(100),
    security          double precision,
    "celestialIndex"  integer,
    "orbitIndex"      integer
);

alter table evesde."mapDenormalize"
    owner to yaml;

create index "ix_evesde_mapDenormalize_constellationID"
    on evesde."mapDenormalize" ("constellationID");

create index "ix_evesde_mapDenormalize_orbitID"
    on evesde."mapDenormalize" ("orbitID");

create index "ix_evesde_mapDenormalize_regionID"
    on evesde."mapDenormalize" ("regionID");

create index "ix_evesde_mapDenormalize_solarSystemID"
    on evesde."mapDenormalize" ("solarSystemID");

create index "ix_evesde_mapDenormalize_typeID"
    on evesde."mapDenormalize" ("typeID");

create index "mapDenormalize_IX_groupConstellation"
    on evesde."mapDenormalize" ("groupID", "constellationID");

create index "mapDenormalize_IX_groupRegion"
    on evesde."mapDenormalize" ("groupID", "regionID");

create index "mapDenormalize_IX_groupSystem"
    on evesde."mapDenormalize" ("groupID", "solarSystemID");

create table evesde."mapJumps"
(
    "stargateID"    integer not null
        primary key,
    "destinationID" integer
);

alter table evesde."mapJumps"
    owner to yaml;

create table evesde."mapLandmarks"
(
    "landmarkID"   integer not null
        primary key,
    "landmarkName" varchar(100),
    description    text,
    "locationID"   integer,
    x              double precision,
    y              double precision,
    z              double precision,
    "iconID"       integer
);

alter table evesde."mapLandmarks"
    owner to yaml;

create table evesde."mapLocationScenes"
(
    "locationID" integer not null
        primary key,
    "graphicID"  integer
);

alter table evesde."mapLocationScenes"
    owner to yaml;

create table evesde."mapLocationWormholeClasses"
(
    "locationID"      integer not null
        primary key,
    "wormholeClassID" integer
);

alter table evesde."mapLocationWormholeClasses"
    owner to yaml;

create table evesde."mapRegionJumps"
(
    "fromRegionID" integer not null,
    "toRegionID"   integer not null,
    primary key ("fromRegionID", "toRegionID")
);

alter table evesde."mapRegionJumps"
    owner to yaml;

create table evesde."mapRegions"
(
    "regionID"   integer not null
        primary key,
    "regionName" varchar(100),
    x            double precision,
    y            double precision,
    z            double precision,
    "xMin"       double precision,
    "xMax"       double precision,
    "yMin"       double precision,
    "yMax"       double precision,
    "zMin"       double precision,
    "zMax"       double precision,
    "factionID"  integer,
    nebula       integer,
    radius       double precision
);

alter table evesde."mapRegions"
    owner to yaml;

create table evesde."mapSecondarySuns"
(
    "itemID"             integer not null
        primary key,
    "solarSystemID"      integer,
    "typeID"             integer,
    "effectBeaconTypeID" integer
);

alter table evesde."mapSecondarySuns"
    owner to yaml;

create index "ix_evesde_mapSecondarySuns_solarSystemID"
    on evesde."mapSecondarySuns" ("solarSystemID");

create table evesde."mapSolarSystemDisallowedAnchors"
(
    "solarSystemID" integer    not null,
    "anchorType"    varchar(8) not null,
    "anchorID"      integer    not null,
    primary key ("solarSystemID", "anchorType", "anchorID")
);

alter table evesde."mapSolarSystemDisallowedAnchors"
    owner to yaml;

create table evesde."mapSolarSystemJumps"
(
    "fromRegionID"        integer,
    "fromConstellationID" integer,
    "fromSolarSystemID"   integer not null,
    "toSolarSystemID"     integer not null,
    "toConstellationID"   integer,
    "toRegionID"          integer,
    primary key ("fromSolarSystemID", "toSolarSystemID")
);

alter table evesde."mapSolarSystemJumps"
    owner to yaml;

create table evesde."mapSolarSystems"
(
    "regionID"        integer,
    "constellationID" integer,
    "solarSystemID"   integer not null
        primary key,
    "solarSystemName" varchar(100),
    x                 double precision,
    y                 double precision,
    z                 double precision,
    "xMin"            double precision,
    "xMax"            double precision,
    "yMin"            double precision,
    "yMax"            double precision,
    "zMin"            double precision,
    "zMax"            double precision,
    luminosity        double precision,
    border            boolean,
    fringe            boolean,
    corridor          boolean,
    hub               boolean,
    international     boolean,
    regional          boolean,
    constellation     boolean,
    security          double precision,
    "factionID"       integer,
    radius            double precision,
    "sunTypeID"       integer,
    "securityClass"   varchar(2),
    "position2Dx"     double precision,
    "position2Dy"     double precision,
    "visualEffect"    varchar(50)
);

alter table evesde."mapSolarSystems"
    owner to yaml;

create index "ix_evesde_mapSolarSystems_constellationID"
    on evesde."mapSolarSystems" ("constellationID");

create index "ix_evesde_mapSolarSystems_regionID"
    on evesde."mapSolarSystems" ("regionID");

create index "ix_evesde_mapSolarSystems_security"
    on evesde."mapSolarSystems" (security);

create table evesde."mapUniverse"
(
    "universeID"   integer not null
        primary key,
    "universeName" varchar(100),
    x              double precision,
    y              double precision,
    z              double precision,
    "xMin"         double precision,
    "xMax"         double precision,
    "yMin"         double precision,
    "yMax"         double precision,
    "zMin"         double precision,
    "zMax"         double precision,
    radius         double precision
);

alter table evesde."mapUniverse"
    owner to yaml;

create table evesde."mercenaryTacticalOperations"
(
    "operationID"       integer not null
        primary key,
    name                varchar(255),
    description         text,
    "dungeonID"         integer,
    "anarchyImpact"     integer,
    "developmentImpact" integer,
    "infomorphBonus"    integer
);

alter table evesde."mercenaryTacticalOperations"
    owner to yaml;

create table evesde."metenoxMoonDrills"
(
    "typeID"                   integer not null
        primary key,
    "miningCycleTime"          integer,
    "miningEfficiency"         double precision,
    "reagentsConsumedPerCycle" integer
);

alter table evesde."metenoxMoonDrills"
    owner to yaml;

create table evesde."milCampaignObjContentTags"
(
    "objectiveID" uuid         not null,
    tag           varchar(100) not null,
    primary key ("objectiveID", tag)
);

alter table evesde."milCampaignObjContentTags"
    owner to yaml;

create table evesde."milCampaignObjectives"
(
    "objectiveID"                 uuid not null
        primary key,
    "campaignID"                  uuid,
    title                         varchar(255),
    subtitle                      text,
    "careerPath"                  varchar(100),
    "targetProgress"              bigint,
    "maxProgressPerParticipant"   integer,
    "presentingCharacterID"       integer,
    "issuerCorporationID"         integer,
    "contributionMethod"          varchar(100),
    "contributionParameters"      jsonb,
    "requiredEnlistmentFactionID" integer,
    "rewardIskAmount"             double precision,
    "rewardIskInterval"           integer,
    "rewardIskCorporationID"      integer,
    "rewardLpAmount"              double precision,
    "rewardLpInterval"            integer,
    "rewardLpCorporationID"       integer,
    "rewardStandingPercent"       double precision,
    "rewardStandingInterval"      integer,
    "rewardStandingFactionID"     integer
);

alter table evesde."milCampaignObjectives"
    owner to yaml;

create index "ix_evesde_milCampaignObjectives_campaignID"
    on evesde."milCampaignObjectives" ("campaignID");

create table evesde."milCampaigns"
(
    "campaignID"       uuid not null
        primary key,
    title              varchar(255),
    subtitle           text,
    "factionID"        integer,
    "targetProgress"   integer,
    "campaignSet"      varchar(100),
    race               varchar(50),
    "themePack"        varchar(100),
    "mapFocusEntityID" integer
);

alter table evesde."milCampaigns"
    owner to yaml;

create table evesde."mstMissionExtraStandings"
(
    "missionID" integer not null,
    "factionID" integer not null,
    standing    double precision,
    primary key ("missionID", "factionID")
);

alter table evesde."mstMissionExtraStandings"
    owner to yaml;

create table evesde."mstMissionMessages"
(
    "missionID"  integer      not null,
    "messageKey" varchar(100) not null,
    text         text,
    primary key ("missionID", "messageKey")
);

alter table evesde."mstMissionMessages"
    owner to yaml;

create table evesde."mstMissions"
(
    "missionID"                 integer not null
        primary key,
    "missionName"               varchar(255),
    "hasStandingRewards"        boolean,
    "factionID"                 integer,
    "corporationID"             integer,
    "agentTypeID"               integer,
    "expirationTime"            integer,
    "initialAgentGiftTypeID"    integer,
    "initialAgentGiftQty"       integer,
    "killDungeonID"             integer,
    "killObjectiveQty"          integer,
    "courierObjectiveTypeID"    integer,
    "courierObjectiveQty"       integer,
    "courierObjectiveSingleton" boolean,
    "bonusRewardTypeID"         integer,
    "bonusRewardQty"            integer,
    "bonusTimeInterval"         integer,
    "rewardTypeID"              integer,
    "rewardQty"                 integer
);

alter table evesde."mstMissions"
    owner to yaml;

create table evesde."npcCharacters"
(
    "characterID"   serial
        primary key,
    "characterName" varchar(100),
    "corporationID" integer,
    "locationID"    integer,
    "raceID"        integer,
    "bloodlineID"   integer,
    "ancestryID"    integer,
    gender          boolean,
    "careerID"      integer,
    "schoolID"      integer,
    "specialityID"  integer,
    "startDate"     varchar(30),
    "isCeo"         boolean
);

alter table evesde."npcCharacters"
    owner to yaml;

create table evesde."ntfTypes"
(
    "typeID"       integer not null
        primary key,
    "internalName" varchar(100),
    "displayName"  varchar(200)
);

alter table evesde."ntfTypes"
    owner to yaml;

create table evesde."planetResources"
(
    "celestialID"           serial
        primary key,
    power                   integer,
    workforce               integer,
    "reagentTypeID"         integer,
    "reagentAmountPerCycle" integer,
    "reagentCyclePeriod"    integer,
    "reagentSecuredCap"     integer,
    "reagentUnsecuredCap"   integer
);

alter table evesde."planetResources"
    owner to yaml;

create table evesde."planetSchematics"
(
    "schematicID"   integer not null
        primary key,
    "schematicName" varchar(255),
    "cycleTime"     integer
);

alter table evesde."planetSchematics"
    owner to yaml;

create table evesde."planetSchematicsPinMap"
(
    "schematicID" integer not null,
    "pinTypeID"   integer not null,
    primary key ("schematicID", "pinTypeID")
);

alter table evesde."planetSchematicsPinMap"
    owner to yaml;

create table evesde."planetSchematicsTypeMap"
(
    "schematicID" integer not null,
    "typeID"      integer not null,
    quantity      integer,
    "isInput"     boolean,
    primary key ("schematicID", "typeID")
);

alter table evesde."planetSchematicsTypeMap"
    owner to yaml;

create table evesde."proximityTraps"
(
    "typeID"                  integer not null
        primary key,
    "dbuffDuration"           integer,
    "showPerimeterLights"     boolean,
    "triggerDelay"            integer,
    "triggerFilterTypeListID" integer,
    "triggerRange"            double precision
);

alter table evesde."proximityTraps"
    owner to yaml;

create table evesde."ramActivities"
(
    "activityID"   integer not null
        primary key,
    "activityName" varchar(100),
    "iconNo"       varchar(5),
    description    varchar(1000),
    published      boolean
);

alter table evesde."ramActivities"
    owner to yaml;

create table evesde."ramAssemblyLineStations"
(
    "stationID"          integer not null,
    "assemblyLineTypeID" integer not null,
    quantity             integer,
    "stationTypeID"      integer,
    "ownerID"            integer,
    "solarSystemID"      integer,
    "regionID"           integer,
    primary key ("stationID", "assemblyLineTypeID")
);

alter table evesde."ramAssemblyLineStations"
    owner to yaml;

create index "ix_evesde_ramAssemblyLineStations_ownerID"
    on evesde."ramAssemblyLineStations" ("ownerID");

create index "ix_evesde_ramAssemblyLineStations_regionID"
    on evesde."ramAssemblyLineStations" ("regionID");

create index "ix_evesde_ramAssemblyLineStations_solarSystemID"
    on evesde."ramAssemblyLineStations" ("solarSystemID");

create table evesde."ramAssemblyLineTypeDetailPerCategory"
(
    "assemblyLineTypeID" integer not null,
    "categoryID"         integer not null,
    "timeMultiplier"     double precision,
    "materialMultiplier" double precision,
    "costMultiplier"     double precision,
    primary key ("assemblyLineTypeID", "categoryID")
);

alter table evesde."ramAssemblyLineTypeDetailPerCategory"
    owner to yaml;

create table evesde."ramAssemblyLineTypeDetailPerGroup"
(
    "assemblyLineTypeID" integer not null,
    "groupID"            integer not null,
    "timeMultiplier"     double precision,
    "materialMultiplier" double precision,
    "costMultiplier"     double precision,
    primary key ("assemblyLineTypeID", "groupID")
);

alter table evesde."ramAssemblyLineTypeDetailPerGroup"
    owner to yaml;

create table evesde."ramAssemblyLineTypeDetailPerTypeList"
(
    "assemblyLineTypeID" integer not null,
    "typeListID"         integer not null,
    "materialMultiplier" double precision,
    "timeMultiplier"     double precision,
    primary key ("assemblyLineTypeID", "typeListID")
);

alter table evesde."ramAssemblyLineTypeDetailPerTypeList"
    owner to yaml;

create table evesde."ramAssemblyLineTypes"
(
    "assemblyLineTypeID"     integer not null
        primary key,
    "assemblyLineTypeName"   varchar(100),
    description              varchar(1000),
    "baseTimeMultiplier"     double precision,
    "baseMaterialMultiplier" double precision,
    "baseCostMultiplier"     double precision,
    volume                   double precision,
    "activityID"             integer,
    "minCostPerHour"         double precision
);

alter table evesde."ramAssemblyLineTypes"
    owner to yaml;

create table evesde."ramAssemblyLines"
(
    "assemblyLineID"         integer not null
        primary key,
    "activityID"             integer,
    "baseCostMultiplier"     double precision,
    "baseMaterialMultiplier" double precision,
    "baseTimeMultiplier"     double precision,
    name                     varchar(100),
    description              text
);

alter table evesde."ramAssemblyLines"
    owner to yaml;

create table evesde."ramInstallationTypeContents"
(
    "installationTypeID" integer not null,
    "assemblyLineTypeID" integer not null,
    quantity             integer,
    primary key ("installationTypeID", "assemblyLineTypeID")
);

alter table evesde."ramInstallationTypeContents"
    owner to yaml;

create table evesde."sdeInfo"
(
    "buildNumber" integer not null
        primary key,
    "releaseDate" timestamp
);

alter table evesde."sdeInfo"
    owner to yaml;

create table evesde."shipSkills"
(
    "typeID"  integer,
    "groupID" integer,
    "skillID" integer,
    level     integer
);

alter table evesde."shipSkills"
    owner to yaml;

create index "ix_evesde_shipSkills_skillID"
    on evesde."shipSkills" ("skillID");

create index "ix_evesde_shipSkills_typeID"
    on evesde."shipSkills" ("typeID");

create table evesde."shipTreeElements"
(
    "elementID" integer not null
        primary key,
    name        varchar(255),
    description text,
    icon        varchar(255)
);

alter table evesde."shipTreeElements"
    owner to yaml;

create table evesde."shipTreeFactionElements"
(
    "factionID" integer not null,
    "elementID" integer not null,
    value       integer,
    primary key ("factionID", "elementID")
);

alter table evesde."shipTreeFactionElements"
    owner to yaml;

create table evesde."shipTreeFactions"
(
    "factionID" integer not null
        primary key,
    description text,
    icon        varchar(512)
);

alter table evesde."shipTreeFactions"
    owner to yaml;

create table evesde."shipTreeGroupElements"
(
    "groupID"   integer not null,
    "elementID" integer not null,
    value       integer,
    primary key ("groupID", "elementID")
);

alter table evesde."shipTreeGroupElements"
    owner to yaml;

create table evesde."shipTreeGroupPreReqSkills"
(
    "groupID"   integer not null,
    "factionID" integer not null,
    "skillID"   integer not null,
    level       integer,
    display     boolean,
    primary key ("groupID", "factionID", "skillID")
);

alter table evesde."shipTreeGroupPreReqSkills"
    owner to yaml;

create table evesde."shipTreeGroups"
(
    "groupID"      integer not null
        primary key,
    name           varchar(255),
    description    text,
    icon           varchar(512),
    "iconLarge"    varchar(512),
    "iconSmall"    varchar(512),
    "iconSmallNPC" varchar(512)
);

alter table evesde."shipTreeGroups"
    owner to yaml;

create table evesde."skillPlanMilestones"
(
    "skillPlanID" integer not null,
    "typeID"      integer not null,
    level         integer,
    primary key ("skillPlanID", "typeID")
);

alter table evesde."skillPlanMilestones"
    owner to yaml;

create table evesde."skillPlanSkillRequirements"
(
    "skillPlanID" integer not null,
    "typeID"      integer not null,
    level         integer not null,
    primary key ("skillPlanID", "typeID", level)
);

alter table evesde."skillPlanSkillRequirements"
    owner to yaml;

create table evesde."skillPlans"
(
    "skillPlanID"  integer not null
        primary key,
    "careerPathID" integer,
    "factionID"    integer,
    "internalName" varchar(200),
    name           varchar(200),
    description    text
);

alter table evesde."skillPlans"
    owner to yaml;

create table evesde."skinLicense"
(
    "licenseTypeID" integer not null
        primary key,
    duration        integer,
    "skinID"        integer
);

alter table evesde."skinLicense"
    owner to yaml;

create table evesde."skinMaterials"
(
    "skinMaterialID" integer not null
        primary key,
    "displayNameID"  integer,
    "materialSetID"  integer
);

alter table evesde."skinMaterials"
    owner to yaml;

create table evesde."skinShip"
(
    "skinID" integer,
    "typeID" integer
);

alter table evesde."skinShip"
    owner to yaml;

create index "ix_evesde_skinShip_skinID"
    on evesde."skinShip" ("skinID");

create index "ix_evesde_skinShip_typeID"
    on evesde."skinShip" ("typeID");

create table evesde."skinrComponentCategories"
(
    "categoryID" integer not null
        primary key,
    name         varchar(100)
);

alter table evesde."skinrComponentCategories"
    owner to yaml;

create table evesde."skinrComponentPointValues"
(
    "categoryID" integer not null,
    "rarityID"   integer not null,
    points       integer,
    primary key ("categoryID", "rarityID")
);

alter table evesde."skinrComponentPointValues"
    owner to yaml;

create table evesde."skinrComponentRarities"
(
    "rarityID" integer not null
        primary key,
    name       varchar(100),
    rank       integer
);

alter table evesde."skinrComponentRarities"
    owner to yaml;

create table evesde."skinrComponentTypes"
(
    "componentID"        integer not null,
    "typeID"             integer not null,
    "licenseUsesGranted" integer,
    primary key ("componentID", "typeID")
);

alter table evesde."skinrComponentTypes"
    owner to yaml;

create table evesde."skinrComponents"
(
    "componentID"          integer not null
        primary key,
    name                   varchar(255),
    "categoryID"           integer,
    "rarityID"             integer,
    finish                 varchar(50),
    published              boolean,
    "iconFile"             varchar(512),
    "resourceFile"         varchar(512),
    "projectionTypeU"      varchar(50),
    "projectionTypeV"      varchar(50),
    "sequenceBinderCount"  integer,
    "sequenceBinderTypeID" integer
);

alter table evesde."skinrComponents"
    owner to yaml;

create table evesde."skinrSkinSlotToMaterial"
(
    "typeID"     integer not null,
    "slotID"     integer not null,
    "materialID" integer,
    primary key ("typeID", "slotID")
);

alter table evesde."skinrSkinSlotToMaterial"
    owner to yaml;

create table evesde."skinrSlotAllowedCategories"
(
    "slotID"     integer not null,
    "categoryID" integer not null,
    primary key ("slotID", "categoryID")
);

alter table evesde."skinrSlotAllowedCategories"
    owner to yaml;

create table evesde."skinrSlotCategories"
(
    "categoryID" integer not null
        primary key,
    name         varchar(100)
);

alter table evesde."skinrSlotCategories"
    owner to yaml;

create table evesde."skinrSlotConfigurationShips"
(
    "configID" integer not null,
    "typeID"   integer not null,
    primary key ("configID", "typeID")
);

alter table evesde."skinrSlotConfigurationShips"
    owner to yaml;

create table evesde."skinrSlotConfigurationSlots"
(
    "configID" integer not null,
    "slotID"   integer not null,
    primary key ("configID", "slotID")
);

alter table evesde."skinrSlotConfigurationSlots"
    owner to yaml;

create table evesde."skinrSlotConfigurations"
(
    "configID"      integer not null
        primary key,
    name            varchar(255),
    priority        integer,
    "allowAllShips" boolean
);

alter table evesde."skinrSlotConfigurations"
    owner to yaml;

create table evesde."skinrSlotNames"
(
    "slotNameID" integer not null
        primary key,
    name         varchar(100)
);

alter table evesde."skinrSlotNames"
    owner to yaml;

create table evesde."skinrSlots"
(
    "slotID"     integer not null
        primary key,
    name         varchar(255),
    "categoryID" integer
);

alter table evesde."skinrSlots"
    owner to yaml;

create table evesde."skinrTierThresholds"
(
    "groupID" integer not null,
    tier      integer not null,
    threshold integer,
    primary key ("groupID", tier)
);

alter table evesde."skinrTierThresholds"
    owner to yaml;

create table evesde.skins
(
    "skinID"             integer not null
        primary key,
    "internalName"       varchar(70),
    "skinMaterialID"     integer,
    "visibleTranquility" boolean,
    "visibleSerenity"    boolean,
    "isStructureSkin"    boolean
);

alter table evesde.skins
    owner to yaml;

create table evesde."sovereigntyUpgrades"
(
    "typeID"                 integer not null
        primary key,
    "mutuallyExclusiveGroup" varchar(100),
    "powerAllocation"        integer,
    "powerProduction"        integer,
    "workforceAllocation"    integer,
    "workforceProduction"    integer,
    "fuelTypeID"             integer,
    "fuelHourlyUpkeep"       integer,
    "fuelStartupCost"        integer
);

alter table evesde."sovereigntyUpgrades"
    owner to yaml;

create table evesde."staOperationServices"
(
    "operationID" integer not null,
    "serviceID"   integer not null,
    primary key ("operationID", "serviceID")
);

alter table evesde."staOperationServices"
    owner to yaml;

create table evesde."staOperationTypes"
(
    "operationID" integer not null,
    "raceID"      integer not null,
    "typeID"      integer,
    primary key ("operationID", "raceID")
);

alter table evesde."staOperationTypes"
    owner to yaml;

create table evesde."staOperations"
(
    "operationID"         integer not null
        primary key,
    "activityID"          integer,
    "operationName"       varchar(100),
    description           text,
    border                double precision,
    corridor              double precision,
    fringe                double precision,
    hub                   double precision,
    "manufacturingFactor" double precision,
    ratio                 double precision,
    "researchFactor"      double precision
);

alter table evesde."staOperations"
    owner to yaml;

create table evesde."staServices"
(
    "serviceID"   integer not null
        primary key,
    "serviceName" varchar(100),
    description   varchar(1000)
);

alter table evesde."staServices"
    owner to yaml;

create table evesde."staStandingsRestrictions"
(
    "typeID"           integer not null,
    "serviceID"        integer not null,
    "standingRequired" double precision,
    primary key ("typeID", "serviceID")
);

alter table evesde."staStandingsRestrictions"
    owner to yaml;

create table evesde."staStationTypes"
(
    "stationTypeID"          integer not null
        primary key,
    "dockEntryX"             double precision,
    "dockEntryY"             double precision,
    "dockEntryZ"             double precision,
    "dockOrientationX"       double precision,
    "dockOrientationY"       double precision,
    "dockOrientationZ"       double precision,
    "operationID"            integer,
    "officeSlots"            integer,
    "reprocessingEfficiency" double precision,
    conquerable              boolean
);

alter table evesde."staStationTypes"
    owner to yaml;

create table evesde."staStations"
(
    "stationID"                bigint not null
        primary key,
    security                   double precision,
    "dockingCostPerVolume"     double precision,
    "maxShipVolumeDockable"    double precision,
    "officeRentalCost"         integer,
    "operationID"              integer,
    "stationTypeID"            integer,
    "corporationID"            integer,
    "solarSystemID"            integer,
    "constellationID"          integer,
    "regionID"                 integer,
    "stationName"              varchar(100),
    x                          double precision,
    y                          double precision,
    z                          double precision,
    "reprocessingEfficiency"   double precision,
    "reprocessingStationsTake" double precision,
    "reprocessingHangarFlag"   integer
);

alter table evesde."staStations"
    owner to yaml;

create index "ix_evesde_staStations_constellationID"
    on evesde."staStations" ("constellationID");

create index "ix_evesde_staStations_corporationID"
    on evesde."staStations" ("corporationID");

create index "ix_evesde_staStations_operationID"
    on evesde."staStations" ("operationID");

create index "ix_evesde_staStations_regionID"
    on evesde."staStations" ("regionID");

create index "ix_evesde_staStations_solarSystemID"
    on evesde."staStations" ("solarSystemID");

create index "ix_evesde_staStations_stationTypeID"
    on evesde."staStations" ("stationTypeID");

create table evesde."sysDbuffEmitterDbuffs"
(
    "typeID"  integer not null,
    "dbuffID" integer not null,
    value     double precision,
    primary key ("typeID", "dbuffID")
);

alter table evesde."sysDbuffEmitterDbuffs"
    owner to yaml;

create table evesde."sysDbuffEmitters"
(
    "typeID"           integer not null
        primary key,
    duration           integer,
    "excludeProtected" boolean,
    interval           integer
);

alter table evesde."sysDbuffEmitters"
    owner to yaml;

create table evesde."sysWideEffectDbuffs"
(
    "solarSystemID" integer not null,
    "dbuffID"       integer not null,
    value           double precision,
    primary key ("solarSystemID", "dbuffID")
);

alter table evesde."sysWideEffectDbuffs"
    owner to yaml;

create table evesde."sysWideEffects"
(
    "solarSystemID"      integer not null
        primary key,
    "eligibleTypeListID" integer
);

alter table evesde."sysWideEffects"
    owner to yaml;

create table evesde."translationTables"
(
    "sourceTable"      varchar(200) not null,
    "destinationTable" varchar(200),
    "translatedKey"    varchar(200) not null,
    "tcGroupID"        integer,
    "tcID"             integer,
    primary key ("sourceTable", "translatedKey")
);

alter table evesde."translationTables"
    owner to yaml;

create table evesde."trnTranslationColumns"
(
    "tcGroupID"  integer,
    "tcID"       integer      not null
        primary key,
    "tableName"  varchar(256) not null,
    "columnName" varchar(128) not null,
    "masterID"   varchar(128)
);

alter table evesde."trnTranslationColumns"
    owner to yaml;

create table evesde."trnTranslationLanguages"
(
    "numericLanguageID" integer not null
        primary key,
    "languageID"        varchar(50),
    "languageName"      varchar(200)
);

alter table evesde."trnTranslationLanguages"
    owner to yaml;

create table evesde."trnTranslations"
(
    "tcID"       integer     not null,
    "keyID"      integer     not null,
    "languageID" varchar(50) not null,
    text         text        not null,
    primary key ("tcID", "keyID", "languageID")
);

alter table evesde."trnTranslations"
    owner to yaml;

create table evesde."typeElements"
(
    "typeID"    integer not null,
    "elementID" integer not null,
    value       integer,
    primary key ("typeID", "elementID")
);

alter table evesde."typeElements"
    owner to yaml;

create table evesde."typeListsExcludedCategoryIDs"
(
    "typeListID" integer not null,
    "categoryID" integer not null,
    primary key ("typeListID", "categoryID")
);

alter table evesde."typeListsExcludedCategoryIDs"
    owner to yaml;

create table evesde."typeListsExcludedGroupIDs"
(
    "typeListID" integer not null,
    "groupID"    integer not null,
    primary key ("typeListID", "groupID")
);

alter table evesde."typeListsExcludedGroupIDs"
    owner to yaml;

create table evesde."typeListsExcludedTypeIDs"
(
    "typeListID" integer not null,
    "typeID"     integer not null,
    primary key ("typeListID", "typeID")
);

alter table evesde."typeListsExcludedTypeIDs"
    owner to yaml;

create table evesde."typeListsHeader"
(
    "typeListID"         serial
        primary key,
    name                 text,
    "displayName"        text,
    "displayDescription" text
);

alter table evesde."typeListsHeader"
    owner to yaml;

create table evesde."typeListsIncludedCategoryIDs"
(
    "typeListID" integer not null,
    "categoryID" integer not null,
    primary key ("typeListID", "categoryID")
);

alter table evesde."typeListsIncludedCategoryIDs"
    owner to yaml;

create table evesde."typeListsIncludedGroupIDs"
(
    "typeListID" integer not null,
    "groupID"    integer not null,
    primary key ("typeListID", "groupID")
);

alter table evesde."typeListsIncludedGroupIDs"
    owner to yaml;

create table evesde."typeListsIncludedTypeIDs"
(
    "typeListID" integer not null,
    "typeID"     integer not null,
    primary key ("typeListID", "typeID")
);

alter table evesde."typeListsIncludedTypeIDs"
    owner to yaml;

create table evesde."warCombatZoneSystems"
(
    "solarSystemID" integer not null
        primary key,
    "combatZoneID"  integer
);

alter table evesde."warCombatZoneSystems"
    owner to yaml;

create table evesde."warCombatZones"
(
    "combatZoneID"   integer not null
        primary key,
    "combatZoneName" varchar(100),
    "factionID"      integer,
    "centerSystemID" integer,
    description      varchar(500)
);

alter table evesde."warCombatZones"
    owner to yaml;

