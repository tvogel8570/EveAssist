package com.eveassist.inventory.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Queries only the blueprint rows belonging to holders the caller got from the viewer's linked data. */
@Repository
class BlueprintQueries {

    record Row(long itemId, int typeId, String typeName, String kind, int materialEfficiency, int timeEfficiency,
            long quantity, int runs, UUID holderId, String holderName, boolean corporation, String place,
            String held, String hangar, String container, boolean matched, int tech, String variant) {
    }

    private static final String ROOT_ID = "COALESCE(a.root_location_id, b.location_id)";
    private static final String PLACE_NAME = "COALESCE(st.\"stationName\", sy.\"solarSystemName\", cs.name, "
            + "'Unknown location ' || " + ROOT_ID + "::text)";
    /**
     * The tech level of what a blueprint makes: 1, 2 or 3, and 0 for none. It is the product's {@code techLevel}
     * attribute (422), which is stored as an integer or a float. A Structure item that has none is read from its meta
     * group: Structure Tech I and Structure Faction are 1, and Structure Tech II is 2. Components, fuel blocks and tools
     * have no tech level in the game, and 0 is what they get.
     */
    private static final String TECH = """
            CASE COALESCE(ta."valueInt", ta."valueFloat"::integer,
                    CASE pm."metaGroupID" WHEN 54 THEN 1 WHEN 52 THEN 1 WHEN 53 THEN 2 END)
                WHEN 1 THEN 1 WHEN 2 THEN 2 WHEN 3 THEN 3 ELSE 0 END""";

    /**
     * The variant of what a blueprint makes, which is a different matter from its tech level: the meta group's name
     * for Faction, Storyline, Officer, Deadspace, Abyssal and the like, and "Structure" for the Upwell items. Tech I,
     * Tech II, Tech III and items with no meta group are not a variant, so they have none.
     */
    private static final String VARIANT = """
            CASE WHEN pm."metaGroupID" IN (52, 53, 54) THEN 'Structure'
                WHEN pm."metaGroupID" IS NULL OR pm."metaGroupID" IN (1, 2, 14) THEN NULL
                ELSE pg."metaGroupName" END""";

    private static final String BLUEPRINTS_CTE = """
            WITH blueprints AS (
                SELECT b.character_id AS holder_id, false AS corporation, b.item_id, b.type_id, b.location_id,
                    b.location_flag, b.quantity, b.material_efficiency, b.time_efficiency, b.runs, c.name AS holder_name
                FROM character_blueprint b JOIN character c ON c.public_id = b.character_id
                WHERE b.character_id = ANY(:characters)
                UNION ALL
                SELECT b.corporation_id, true, b.item_id, b.type_id, b.location_id, b.location_flag, b.quantity,
                    b.material_efficiency, b.time_efficiency, b.runs, c.name
                FROM corporation_blueprint b JOIN corporation c ON c.public_id = b.corporation_id
                WHERE b.corporation_id = ANY(:corporations)
            )
            """;

    private static final String COMMON_FROM = """
            FROM blueprints b
            LEFT JOIN holder_asset a ON a.holder_id = b.holder_id AND a.item_id = b.item_id
            LEFT JOIN sde."invTypes" ty ON ty."typeID" = b.type_id
            LEFT JOIN sde."staStations" st ON st."stationID" = %s
            LEFT JOIN sde."mapSolarSystems" sy ON sy."solarSystemID" = %s
            LEFT JOIN LATERAL (
                SELECT k.name, k.solar_system_id FROM character_structure k
                WHERE k.character_id = ANY(:structureCharacters) AND k.structure_id = %s AND k.status = 'FOUND'
                ORDER BY k.asked_at DESC LIMIT 1
            ) cs ON %s >= 1000000000000
            LEFT JOIN sde."mapSolarSystems" csy ON csy."solarSystemID" = cs.solar_system_id
            LEFT JOIN sde."mapRegions" rg ON rg."regionID" = COALESCE(st."regionID", sy."regionID", csy."regionID")
            LEFT JOIN holder_asset container ON container.holder_id = a.holder_id
                AND container.item_id = a.container_id
            LEFT JOIN sde."invTypes" container_type ON container_type."typeID" = a.container_type_id
            LEFT JOIN corporation_division division ON division.corporation_id = b.holder_id
                AND division.division = a.division
            LEFT JOIN sde."industryActivityProducts" ap ON ap."typeID" = b.type_id AND ap."activityID" = 1
            LEFT JOIN sde."dgmTypeAttributes" ta ON ta."typeID" = ap."productTypeID" AND ta."attributeID" = 422
            LEFT JOIN sde."invMetaTypes" pm ON pm."typeID" = ap."productTypeID"
            LEFT JOIN sde."invMetaGroups" pg ON pg."metaGroupID" = pm."metaGroupID"
            """.formatted(ROOT_ID, ROOT_ID, ROOT_ID, ROOT_ID);

    private final JdbcClient jdbc;

    BlueprintQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    List<Row> list(UUID[] characters, UUID[] corporations, UUID[] structureCharacters, BlueprintFilter filter) {
        return list(characters, corporations, structureCharacters, filter, null);
    }

    /** The rows that match, without the paging: how many there are, so the page can say how many it leaves out. */
    long count(UUID[] characters, UUID[] corporations, UUID[] structureCharacters, BlueprintFilter filter) {
        var selection = selection(characters, corporations, structureCharacters, filter, null);
        return jdbc.sql("%s SELECT count(*) %s%s".formatted(BLUEPRINTS_CTE, COMMON_FROM, selection.where()))
                .params(selection.params()).query(Long.class).single();
    }

    /**
     * How many blueprints each tech level has under the filter, without the filter's own tech level, so that the buttons
     * above the table say what each would show. A level with none is left out.
     */
    Map<Integer, Long> techCounts(UUID[] characters, UUID[] corporations, UUID[] structureCharacters,
            BlueprintFilter filter) {
        var selection = selection(characters, corporations, structureCharacters, filter.withTech(null), null);
        var counts = new java.util.TreeMap<Integer, Long>();
        jdbc.sql("%s SELECT %s AS tech, count(*) AS n %s%s GROUP BY 1".formatted(BLUEPRINTS_CTE, TECH, COMMON_FROM,
                        selection.where()))
                .params(selection.params())
                .query((rs, row) -> counts.put(rs.getInt("tech"), rs.getLong("n"))).list();
        return counts;
    }

    private record Selection(String where, Map<String, Object> params) {
    }

    private Selection selection(UUID[] characters, UUID[] corporations, UUID[] structureCharacters,
            BlueprintFilter filter, java.util.Collection<Long> itemIds) {
        var conditions = new ArrayList<String>();
        var params = params(characters, corporations, structureCharacters);
        if (itemIds != null) {
            conditions.add("b.item_id = ANY(:itemIds)");
            params.put("itemIds", itemIds.toArray(Long[]::new));
        }
        if (filter.typeId() != null) {
            conditions.add("b.type_id = :type");
            params.put("type", filter.typeId());
        }
        if (filter.tech() != null) {
            conditions.add("(" + TECH + ") = :tech");
            params.put("tech", filter.tech());
        }
        if (!filter.kind().isBlank()) {
            conditions.add("CASE WHEN b.quantity = -2 THEN 'BPC' ELSE 'BPO' END = :kind");
            params.put("kind", filter.kind());
        }
        if (filter.minimumMe() > 0) {
            conditions.add("b.material_efficiency >= :me");
            params.put("me", filter.minimumMe());
        }
        if (filter.minimumTe() > 0) {
            conditions.add("b.time_efficiency >= :te");
            params.put("te", filter.minimumTe());
        }
        if (filter.holder() != null) {
            conditions.add("b.holder_id = :holder");
            params.put("holder", filter.holder());
        }
        if (filter.needsResearch()) {
            conditions.add("b.quantity <> -2 AND (b.material_efficiency < 10 OR b.time_efficiency < 20)");
        }
        if (!filter.search().isBlank()) {
            conditions.add("(ty.\"typeName\" ILIKE :search OR " + PLACE_NAME + " ILIKE :search "
                    + "OR b.holder_name ILIKE :search)");
            params.put("search", "%" + escaped(filter.search()) + "%");
        }
        return new Selection(conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions), params);
    }

    /**
     * As above, and with {@code itemIds} not null, only those items: a drawer or an export needs a few rows. The
     * filter's limit, when it has one, keeps only the first rows in the order asked for.
     */
    List<Row> list(UUID[] characters, UUID[] corporations, UUID[] structureCharacters, BlueprintFilter filter,
            java.util.Collection<Long> itemIds) {
        var selection = selection(characters, corporations, structureCharacters, filter, itemIds);
        var params = selection.params();
        var order = switch (filter.sort()) {
            case "kind" -> "b.quantity";
            // every blueprint has the same status until industry jobs are read, and ORDER BY refuses a bare literal
            case "status" -> "CAST('NOT_READ' AS text)";
            // 1, 2, 3 and then the blueprints with no tech level
            case "tech" -> "CASE (" + TECH + ") WHEN 0 THEN 9 ELSE (" + TECH + ") END";
            case "me" -> "b.material_efficiency";
            case "te" -> "b.time_efficiency";
            case "runs" -> "b.runs";
            case "holder" -> "lower(b.holder_name)";
            case "place" -> "lower(" + PLACE_NAME + ")";
            default -> "lower(COALESCE(ty.\"typeName\", 'Type ' || b.type_id::text))";
        };
        var direction = "desc".equals(filter.direction()) ? "DESC" : "ASC";
        var limit = filter.limit() > 0 ? " LIMIT :limit" : "";
        if (filter.limit() > 0) {
            params.put("limit", filter.limit());
        }
        return jdbc.sql("""
                %s
                SELECT b.item_id, b.type_id, COALESCE(ty."typeName", 'Type ' || b.type_id::text) AS type_name,
                    CASE WHEN b.quantity = -2 THEN 'BPC' ELSE 'BPO' END AS kind,
                    b.material_efficiency, b.time_efficiency, b.quantity, b.runs, b.holder_id, b.holder_name,
                    b.corporation, %s AS place,
                    COALESCE(a.held, b.location_flag) AS held,
                    CASE WHEN a.division IS NOT NULL THEN COALESCE(division.name, 'Hangar ' || a.division::text) END AS hangar,
                    CASE WHEN a.container_id IS NOT NULL THEN COALESCE(NULLIF(container.name, ''),
                        container_type."typeName", 'Container') END AS container,
                    a.item_id IS NOT NULL AS matched, %s AS tech, %s AS variant
                %s%s
                ORDER BY %s %s, b.item_id%s
                """.formatted(BLUEPRINTS_CTE, PLACE_NAME, TECH, VARIANT, COMMON_FROM, selection.where(), order, direction,
                limit))
                .params(params)
                .query((rs, row) -> new Row(rs.getLong("item_id"), rs.getInt("type_id"), rs.getString("type_name"),
                        rs.getString("kind"), rs.getInt("material_efficiency"), rs.getInt("time_efficiency"),
                        rs.getLong("quantity"), rs.getInt("runs"), rs.getObject("holder_id", UUID.class),
                        rs.getString("holder_name"), rs.getBoolean("corporation"), rs.getString("place"),
                        rs.getString("held"), rs.getString("hangar"), rs.getString("container"),
                        rs.getBoolean("matched"), rs.getInt("tech"), rs.getString("variant")))
                .list();
    }

    private static Map<String, Object> params(UUID[] characters, UUID[] corporations, UUID[] structureCharacters) {
        var params = new HashMap<String, Object>();
        params.put("characters", characters);
        params.put("corporations", corporations);
        params.put("structureCharacters", structureCharacters);
        return params;
    }

    private static String escaped(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}