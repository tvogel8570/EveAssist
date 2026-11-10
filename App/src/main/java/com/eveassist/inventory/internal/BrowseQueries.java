package com.eveassist.inventory.internal;

import com.eveassist.inventory.internal.BrowseFilter.Dimension;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The inventory page's questions to the database. Every query is limited to the characters it is given, which the
 * caller took from the viewer's own linked characters, so no query can reach another account's assets. All values are
 * bound as parameters, and the one part of the SQL chosen by the caller, the sort, comes from a fixed list.
 */
@Repository
class BrowseQueries {

    /** An item type, its total and who holds it. {@code holders} and {@code held} line up, biggest first. */
    record ItemRow(int typeId, String name, String groupName, String categoryName, long total, int places,
            boolean hasBlueprintCopy, boolean hasBlueprintOriginal, List<UUID> holders, List<Long> held) {
    }

    record Summary(int itemTypes, long units) {
    }

    /** One place an item type is held, how, and by whom. */
    record WhereRow(long locationId, String placeName, String placeKind, String regionName, Held held,
            Map<UUID, Long> byCharacter, long total) {
    }

    /** One value of a column that can be filtered, and how many item types it would leave. */
    record Facet(String value, String label, int itemTypes) {
    }

    /** One line of the export: an item type in one place, held one way by one character. */
    record ExportRow(String item, String category, String group, UUID character, String place, String region,
            Held held, long quantity, String blueprint) {
    }

    private static final long FIRST_STRUCTURE_ID = StructureRepository.FIRST_STRUCTURE_ID;
    private static final String REGION_ID = "COALESCE(st.\"regionID\", sy.\"regionID\", csy.\"regionID\", 0)";
    private static final String PLACE_NAME = "COALESCE(st.\"stationName\", sy.\"solarSystemName\", cs.name)";
    private static final String PLACE_KIND = """
            CASE WHEN st."stationID" IS NOT NULL THEN 'STATION' WHEN sy."solarSystemID" IS NOT NULL THEN 'SYSTEM'
                 WHEN cs.name IS NOT NULL THEN 'STRUCTURE'
                 WHEN a.root_location_id >= :firstStructure THEN 'UNKNOWN_STRUCTURE' ELSE 'UNKNOWN' END""";

    /**
     * Assets with their item type, group, category and place. A place is a station or solar system from the SDE, or a
     * structure named by any character of the account, or nothing known. Names learned by one character serve all of the
     * account's characters, and no one else's.
     */
    private static final String FROM = """
            FROM character_asset a
            JOIN sde."invTypes" ty ON ty."typeID" = a.type_id
            JOIN sde."invGroups" g ON g."groupID" = ty."groupID"
            JOIN sde."invCategories" cat ON cat."categoryID" = g."categoryID"
            LEFT JOIN sde."staStations" st ON st."stationID" = a.root_location_id
            LEFT JOIN sde."mapSolarSystems" sy ON sy."solarSystemID" = a.root_location_id
            LEFT JOIN LATERAL (
                SELECT k.name, k.solar_system_id FROM character_structure k
                WHERE k.character_id = ANY(:chars) AND k.structure_id = a.root_location_id AND k.status = 'FOUND'
                ORDER BY k.asked_at DESC LIMIT 1) cs ON a.root_location_id >= :firstStructure
            LEFT JOIN sde."mapSolarSystems" csy ON csy."solarSystemID" = cs.solar_system_id
            LEFT JOIN sde."mapRegions" rg ON rg."regionID" = COALESCE(st."regionID", sy."regionID", csy."regionID")
            """;

    private final JdbcClient jdbc;

    BrowseQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** A WHERE clause and its parameters. */
    private record Where(String sql, Map<String, Object> params) {
    }

    private static Where where(UUID[] characters, BrowseFilter filter, Dimension skip) {
        var conditions = new ArrayList<String>();
        var params = new HashMap<String, Object>();
        params.put("chars", characters);
        params.put("firstStructure", FIRST_STRUCTURE_ID);
        conditions.add("a.character_id = ANY(:chars)");
        if (!filter.search().isEmpty()) {
            conditions.add("(ty.\"typeName\" ILIKE :q OR g.\"groupName\" ILIKE :q OR cat.\"categoryName\" ILIKE :q)");
            params.put("q", "%" + filter.search().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        }
        if (skip != Dimension.CATEGORY && !filter.categories().isEmpty()) {
            conditions.add("g.\"categoryID\" = ANY(:categories)");
            params.put("categories", filter.categories().toArray(Integer[]::new));
        }
        if (skip != Dimension.GROUP && !filter.groups().isEmpty()) {
            conditions.add("ty.\"groupID\" = ANY(:groups)");
            params.put("groups", filter.groups().toArray(Integer[]::new));
        }
        if (skip != Dimension.CHARACTER && !filter.characters().isEmpty()) {
            conditions.add("a.character_id = ANY(:selected)");
            params.put("selected", filter.characters().toArray(UUID[]::new));
        }
        if (skip != Dimension.PLACE && !filter.places().isEmpty()) {
            conditions.add("a.root_location_id = ANY(:places)");
            params.put("places", filter.places().toArray(Long[]::new));
        }
        if (skip != Dimension.REGION && !filter.regions().isEmpty()) {
            conditions.add(REGION_ID + " = ANY(:regions)");
            params.put("regions", filter.regions().toArray(Integer[]::new));
        }
        if (skip != Dimension.HOW && !filter.held().isEmpty()) {
            conditions.add("a.held = ANY(:held)");
            params.put("held", filter.held().stream().map(Enum::name).toArray(String[]::new));
        }
        return new Where(" WHERE " + String.join(" AND ", conditions), params);
    }

    /** The item types that pass the filter, by the chosen sort, one page of them. */
    List<ItemRow> items(UUID[] characters, BrowseFilter filter) {
        return items(characters, filter, filter.limit());
    }

    /** The same, with a page size of the caller's choosing, for the copy of the whole list. */
    List<ItemRow> items(UUID[] characters, BrowseFilter filter, int limit) {
        var where = where(characters, filter, null);
        var params = new HashMap<>(where.params());
        params.put("min", filter.minTotal());
        params.put("limit", limit);
        var order = switch (filter.sort()) {
            case TOTAL -> "t.total DESC, t.name";
            case NAME -> "t.name";
            case CATEGORY -> "t.category_name, t.group_name, t.name";
        };
        return jdbc.sql("WITH f AS (SELECT a.type_id, ty.\"typeName\" AS name, g.\"groupName\" AS group_name, "
                        + "cat.\"categoryName\" AS category_name, cat.\"categoryID\" AS category_id, a.character_id, "
                        + "a.quantity, a.root_location_id, a.blueprint_copy " + FROM + where.sql() + "), "
                        + """
                        per_character AS (
                            SELECT type_id, character_id, sum(quantity)::bigint AS q FROM f GROUP BY type_id, character_id),
                        t AS (
                            SELECT type_id, min(name) AS name, min(group_name) AS group_name,
                                   min(category_name) AS category_name, sum(quantity) AS total,
                                   count(DISTINCT root_location_id) AS places,
                                   bool_or(blueprint_copy IS TRUE) AS has_copy,
                                   bool_or(category_id = 9 AND blueprint_copy IS NOT TRUE) AS has_original
                            FROM f GROUP BY type_id HAVING sum(quantity) >= :min)
                        SELECT t.*,
                               (SELECT array_agg(p.character_id ORDER BY p.q DESC, p.character_id)
                                FROM per_character p WHERE p.type_id = t.type_id) AS holders,
                               (SELECT array_agg(p.q ORDER BY p.q DESC, p.character_id)
                                FROM per_character p WHERE p.type_id = t.type_id) AS held
                        FROM t ORDER BY\s""" + order + " LIMIT :limit")
                .params(params)
                .query((rs, row) -> new ItemRow(rs.getInt("type_id"), rs.getString("name"), rs.getString("group_name"),
                        rs.getString("category_name"), rs.getLong("total"), rs.getInt("places"),
                        rs.getBoolean("has_copy"), rs.getBoolean("has_original"),
                        List.of((UUID[]) rs.getArray("holders").getArray()),
                        java.util.Arrays.stream((Long[]) rs.getArray("held").getArray()).toList()))
                .list();
    }

    /** The item types whose total, within the filter, reaches the minimum. The export and the lists of values use it. */
    List<Integer> typesAtLeastMinimum(UUID[] characters, BrowseFilter filter) {
        var where = where(characters, filter, null);
        var params = new HashMap<>(where.params());
        params.put("min", filter.minTotal());
        return jdbc.sql("SELECT a.type_id " + FROM + where.sql() + " GROUP BY a.type_id HAVING sum(a.quantity) >= :min")
                .params(params).query(Integer.class).list();
    }

    /** How many item types pass the filter and how many units they come to, before the page limit. */
    Summary summary(UUID[] characters, BrowseFilter filter) {
        var where = where(characters, filter, null);
        var params = new HashMap<>(where.params());
        params.put("min", filter.minTotal());
        return jdbc.sql("WITH f AS (SELECT a.type_id, a.quantity " + FROM + where.sql() + "), "
                        + "t AS (SELECT type_id, sum(quantity) AS total FROM f GROUP BY type_id HAVING sum(quantity) >= :min) "
                        + "SELECT count(*) AS item_types, coalesce(sum(total), 0) AS units FROM t")
                .params(params)
                .query((rs, row) -> new Summary(rs.getInt("item_types"), rs.getLong("units")))
                .single();
    }

    /** Where one item type is, within the filter: one row for each place and way of holding. */
    List<WhereRow> where(UUID[] characters, BrowseFilter filter, int typeId) {
        var where = where(characters, filter, null);
        var params = new HashMap<>(where.params());
        params.put("type", typeId);
        var rows = jdbc.sql("SELECT a.root_location_id, " + PLACE_NAME + " AS place_name, " + PLACE_KIND
                        + " AS place_kind, rg.\"regionName\" AS region_name, a.held, a.character_id, "
                        + "sum(a.quantity) AS quantity " + FROM + where.sql() + " AND a.type_id = :type "
                        + "GROUP BY a.root_location_id, place_name, place_kind, region_name, a.held, a.character_id")
                .params(params)
                .query((rs, row) -> new Object[] {rs.getLong("root_location_id"), rs.getString("place_name"),
                        rs.getString("place_kind"), rs.getString("region_name"), Held.valueOf(rs.getString("held")),
                        rs.getObject("character_id", UUID.class), rs.getLong("quantity")})
                .list();
        var grouped = new LinkedHashMap<String, WhereRow>();
        for (var r : rows) {
            var key = r[0] + "|" + r[4];
            var existing = grouped.get(key);
            var by = existing == null ? new LinkedHashMap<UUID, Long>() : new LinkedHashMap<>(existing.byCharacter());
            by.merge((UUID) r[5], (Long) r[6], Long::sum);
            grouped.put(key, new WhereRow((Long) r[0], (String) r[1], (String) r[2], (String) r[3], (Held) r[4], by,
                    by.values().stream().mapToLong(Long::longValue).sum()));
        }
        return grouped.values().stream().sorted((x, y) -> Long.compare(y.total(), x.total())).toList();
    }

    /**
     * The values a column offers, each with the number of item types that would be left if it were the only value
     * chosen in that column, given every other filter. The column's own filter is left out, so that the choice can be
     * widened.
     */
    List<Facet> facet(UUID[] characters, Dimension dimension, BrowseFilter filter) {
        return facet(characters, dimension, filter, null);
    }

    /** The most values a list returns. A search in the list narrows it, so the rest stay reachable. */
    static final int MOST_FACET_VALUES = 500;

    /**
     * With a search, only values whose name contains it are returned. The names of characters and ways of holding are
     * not in the database, so the caller searches those.
     */
    List<Facet> facet(UUID[] characters, Dimension dimension, BrowseFilter filter, String search) {
        var where = where(characters, filter, dimension);
        var sql = where.sql();
        var params = new HashMap<>(where.params());
        if (filter.minTotal() > 0) {
            // the counts must agree with the list: only item types that reach the minimum under the other filters
            var types = typesAtLeastMinimum(characters, filter.without(dimension));
            if (types.isEmpty()) {
                return List.of();
            }
            sql += " AND a.type_id = ANY(:types)";
            params.put("types", types.toArray(Integer[]::new));
        }
        var columns = switch (dimension) {
            case CATEGORY -> new String[] {"g.\"categoryID\"::text", "cat.\"categoryName\""};
            case GROUP -> new String[] {"ty.\"groupID\"::text", "g.\"groupName\""};
            case CHARACTER -> new String[] {"a.character_id::text", "a.character_id::text"};
            case PLACE -> new String[] {"a.root_location_id::text", "COALESCE(" + PLACE_NAME + ", '')"};
            case REGION -> new String[] {REGION_ID + "::text", "COALESCE(rg.\"regionName\", '')"};
            case HOW -> new String[] {"a.held", "a.held"};
        };
        var named = dimension != Dimension.CHARACTER && dimension != Dimension.HOW;
        var having = "";
        if (named && search != null && !search.isBlank()) {
            having = " HAVING min(" + columns[1] + ") ILIKE :search";
            params.put("search", "%" + search.strip().replace("\\", "\\\\").replace("%", "\\%")
                    .replace("_", "\\_") + "%");
        }
        return jdbc.sql("SELECT " + columns[0] + " AS value, min(" + columns[1] + ") AS label, "
                        + "count(DISTINCT a.type_id) AS item_types " + FROM + sql + " GROUP BY 1" + having
                        + " ORDER BY 2, 1 LIMIT " + MOST_FACET_VALUES)
                .params(params)
                .query((rs, row) -> new Facet(rs.getString("value"), rs.getString("label"), rs.getInt("item_types")))
                .list();
    }

    /** The names of a place, a category, a group or a region by id, for the chips that show a chosen value. */
    Map<String, String> labels(UUID[] characters, Dimension dimension, Collection<String> values) {
        var labels = new LinkedHashMap<String, String>();
        if (values.isEmpty()) {
            return labels;
        }
        switch (dimension) {
            case CATEGORY -> jdbc.sql("SELECT \"categoryID\"::text AS id, \"categoryName\" AS label FROM sde.\"invCategories\" "
                            + "WHERE \"categoryID\"::text = ANY(:ids)")
                    .param("ids", values.toArray(String[]::new))
                    .query((rs, row) -> labels.put(rs.getString("id"), rs.getString("label"))).list();
            case GROUP -> jdbc.sql("SELECT \"groupID\"::text AS id, \"groupName\" AS label FROM sde.\"invGroups\" "
                            + "WHERE \"groupID\"::text = ANY(:ids)")
                    .param("ids", values.toArray(String[]::new))
                    .query((rs, row) -> labels.put(rs.getString("id"), rs.getString("label"))).list();
            case REGION -> jdbc.sql("SELECT \"regionID\"::text AS id, \"regionName\" AS label FROM sde.\"mapRegions\" "
                            + "WHERE \"regionID\"::text = ANY(:ids)")
                    .param("ids", values.toArray(String[]::new))
                    .query((rs, row) -> labels.put(rs.getString("id"), rs.getString("label"))).list();
            case PLACE -> jdbc.sql("SELECT DISTINCT a.root_location_id::text AS id, " + PLACE_NAME + " AS label " + FROM
                            + " WHERE a.character_id = ANY(:chars) AND a.root_location_id::text = ANY(:ids)")
                    .param("chars", characters).param("firstStructure", FIRST_STRUCTURE_ID)
                    .param("ids", values.toArray(String[]::new))
                    .query((rs, row) -> labels.put(rs.getString("id"), rs.getString("label"))).list();
            default -> {
                // characters and ways of holding are named by the caller
            }
        }
        return labels;
    }

    /** Every line the filter lets through, for the export. At most this many, so one request cannot read it all. */
    static final int MOST_EXPORT_ROWS = 100_000;

    List<ExportRow> export(UUID[] characters, BrowseFilter filter) {
        var where = where(characters, filter, null);
        var sql = where.sql();
        var params = new HashMap<>(where.params());
        if (filter.minTotal() > 0) {
            // the minimum is on an item type's total, so the types that reach it are found first
            var types = typesAtLeastMinimum(characters, filter);
            if (types.isEmpty()) {
                return List.of();
            }
            sql += " AND a.type_id = ANY(:types)";
            params.put("types", types.toArray(Integer[]::new));
        }
        return jdbc.sql("SELECT ty.\"typeName\" AS item, cat.\"categoryName\" AS category, g.\"groupName\" AS grp, "
                        + "a.character_id, " + PLACE_NAME + " AS place_name, a.root_location_id, "
                        + "rg.\"regionName\" AS region_name, a.held, sum(a.quantity) AS quantity, "
                        + "CASE WHEN a.blueprint_copy IS TRUE THEN 'copy' WHEN cat.\"categoryID\" = 9 THEN 'original' "
                        + "ELSE '' END AS blueprint " + FROM + sql
                        + " GROUP BY item, category, grp, a.character_id, place_name, a.root_location_id, region_name, "
                        + "a.held, blueprint ORDER BY item, a.character_id, a.root_location_id LIMIT " + MOST_EXPORT_ROWS)
                .params(params)
                .query((rs, row) -> new ExportRow(rs.getString("item"), rs.getString("category"), rs.getString("grp"),
                        rs.getObject("character_id", UUID.class),
                        rs.getString("place_name") != null ? rs.getString("place_name")
                                : (rs.getLong("root_location_id") >= FIRST_STRUCTURE_ID ? "Unknown structure "
                                        : "Unknown location ") + rs.getLong("root_location_id"),
                        rs.getString("region_name") == null ? "" : rs.getString("region_name"),
                        Held.valueOf(rs.getString("held")), rs.getLong("quantity"), rs.getString("blueprint")))
                .list();
    }
}
