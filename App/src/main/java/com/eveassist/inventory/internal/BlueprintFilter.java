package com.eveassist.inventory.internal;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** The Blueprints table's address: every search, filter and sort can be bookmarked and shared. */
public record BlueprintFilter(String search, Integer typeId, String kind, int minimumMe, int minimumTe,
        UUID holder, boolean needsResearch, String status, String sort, String direction, int limit) {

    /** How many blueprints the first page shows, and how many each "Show more" adds. */
    public static final int FIRST_PAGE = 100;
    /** The most the page ever lists. A bigger list is reached by narrowing the filters. */
    public static final int MOST_ITEMS = 1_000;
    /** The limit of a filter that takes every row it matches, for the drawer and the export. */
    public static final int NO_LIMIT = 0;

    /** A filter for every blueprint of the viewer, in type order. */
    public static BlueprintFilter all() {
        return new BlueprintFilter("", null, "", 0, 0, null, false, "", "type", "asc", NO_LIMIT);
    }

    public BlueprintFilter withLimit(int newLimit) {
        return new BlueprintFilter(search, typeId, kind, minimumMe, minimumTe, holder, needsResearch, status, sort,
                direction, newLimit);
    }

    public static BlueprintFilter parse(Map<String, String[]> parameters) {
        var type = integer(first(parameters, "type"));
        var holder = uuid(first(parameters, "holder"));
        var kind = first(parameters, "kind");
        if (!"BPO".equals(kind) && !"BPC".equals(kind)) {
            kind = "";
        }
        var status = "not_read".equals(first(parameters, "status")) ? "not_read" : "";
        var sort = first(parameters, "sort");
        if (!java.util.Set.of("type", "kind", "status", "me", "te", "runs", "holder", "place")
                .contains(sort)) {
            sort = "type";
        }
        var direction = "desc".equalsIgnoreCase(first(parameters, "dir")) ? "desc" : "asc";
        return new BlueprintFilter(first(parameters, "q"), type, kind, bounded(parameters, "me", 0, 10),
                bounded(parameters, "te", 0, 20), holder, "1".equals(first(parameters, "research")), status, sort,
                direction, limit(first(parameters, "limit")));
    }

    private static int limit(String value) {
        try {
            return Math.clamp(Integer.parseInt(value), FIRST_PAGE, MOST_ITEMS);
        } catch (NumberFormatException ignored) {
            return FIRST_PAGE;
        }
    }

    private static String first(Map<String, String[]> parameters, String name) {
        var values = parameters.get(name);
        return values == null || values.length == 0 ? "" : values[0];
    }

    private static Integer integer(String value) {
        try {
            var parsed = Integer.valueOf(value);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static UUID uuid(String value) {
        try {
            return value.isBlank() ? null : UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static int bounded(Map<String, String[]> parameters, String name, int min, int max) {
        try {
            return Math.clamp(Integer.parseInt(first(parameters, name)), min, max);
        } catch (NumberFormatException ignored) {
            return min;
        }
    }

    public String query() {
        var params = new LinkedHashMap<String, String>();
        put(params, "q", search);
        if (typeId != null) {
            params.put("type", typeId.toString());
        }
        put(params, "kind", kind);
        if (minimumMe > 0) {
            params.put("me", Integer.toString(minimumMe));
        }
        if (minimumTe > 0) {
            params.put("te", Integer.toString(minimumTe));
        }
        if (holder != null) {
            params.put("holder", holder.toString());
        }
        if (needsResearch) {
            params.put("research", "1");
        }
        put(params, "status", status);
        params.put("sort", sort);
        params.put("dir", direction);
        if (limit != FIRST_PAGE) {
            params.put("limit", Integer.toString(limit));
        }
        return params.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(java.util.stream.Collectors.joining("&"));
    }

    public String sortQuery(String column) {
        var nextDirection = sort.equals(column) && "asc".equals(direction) ? "desc" : "asc";
        // a new order starts again at the first page
        return new BlueprintFilter(search, typeId, kind, minimumMe, minimumTe, holder, needsResearch, status, column,
                nextDirection, FIRST_PAGE).query();
    }

    private static void put(Map<String, String> params, String key, String value) {
        if (value != null && !value.isBlank()) {
            params.put(key, value);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** The address of the same list with a page more. */
    public String moreQuery() {
        return withLimit(Math.min(MOST_ITEMS, limit + FIRST_PAGE)).query();
    }
}
