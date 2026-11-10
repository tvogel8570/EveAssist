package com.eveassist.inventory.internal;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;

/**
 * What the inventory page shows, as the address says it. The filters combine across columns (an item must pass all of
 * them) and a column's values mean "any of". Nothing here is trusted: every value is parsed, bad ones are dropped, and
 * the sizes are capped, because the whole filter arrives in a query string.
 *
 * @param showCharacters whether the page shows who holds each item. On when the address says nothing.
 * @param limit          how many item types to list. The page offers more.
 */
public record BrowseFilter(String search, Set<Integer> categories, Set<Integer> groups, Set<UUID> characters, Set<Long> places,
        Set<Integer> regions, Set<Held> held, long minTotal, Sort sort, boolean showCharacters, int limit) {

    public enum Sort { TOTAL, NAME, CATEGORY }

    /** A column that can be filtered. The name is the query parameter. */
    public enum Dimension {
        CATEGORY("category"), GROUP("group"), CHARACTER("character"), PLACE("place"), REGION("region"), HOW("how");

        public final String parameter;

        Dimension(String parameter) {
            this.parameter = parameter;
        }

        public static Dimension of(String parameter) {
            for (var dimension : values()) {
                if (dimension.parameter.equals(parameter)) {
                    return dimension;
                }
            }
            return null;
        }
    }

    public static final int FIRST_PAGE = 100;
    static final int MOST_ITEMS = 1_000;
    private static final int MOST_VALUES = 200;
    private static final int LONGEST_SEARCH = 100;

    public BrowseFilter {
        search = search == null ? "" : search.strip();
        if (search.length() > LONGEST_SEARCH) {
            search = search.substring(0, LONGEST_SEARCH);
        }
        categories = Set.copyOf(categories);
        groups = Set.copyOf(groups);
        characters = Set.copyOf(characters);
        places = Set.copyOf(places);
        regions = Set.copyOf(regions);
        held = Set.copyOf(held);
        minTotal = Math.max(0, minTotal);
        limit = Math.min(MOST_ITEMS, Math.max(1, limit));
    }

    public static BrowseFilter none() {
        return new BrowseFilter("", Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), 0, Sort.TOTAL, true,
                FIRST_PAGE);
    }

    /** Reads the request's parameters. Anything that does not parse is ignored. */
    public static BrowseFilter parse(Map<String, String[]> parameters) {
        Function<String, List<String>> values = name -> {
            var raw = parameters.get(name);
            return raw == null ? List.of() : java.util.Arrays.stream(raw).limit(MOST_VALUES).toList();
        };
        var sort = Sort.TOTAL;
        for (var candidate : values.apply("sort")) {
            try {
                sort = Sort.valueOf(candidate.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // an unknown sort is the default
            }
        }
        var formSent = !values.apply("ui").isEmpty();
        return new BrowseFilter(
                values.apply("q").stream().findFirst().orElse(""),
                parseAll(values.apply("category"), Integer::valueOf),
                parseAll(values.apply("group"), Integer::valueOf),
                parseAll(values.apply("character"), UUID::fromString),
                parseAll(values.apply("place"), Long::valueOf),
                parseAll(values.apply("region"), Integer::valueOf),
                parseAll(values.apply("how"), text -> Held.valueOf(text.toUpperCase(java.util.Locale.ROOT))),
                values.apply("min").stream().findFirst().map(BrowseFilter::longOrZero).orElse(0L),
                sort,
                !formSent || !values.apply("chars").isEmpty(),
                values.apply("limit").stream().findFirst().map(text -> (int) Math.min(MOST_ITEMS, longOrZero(text)))
                        .filter(limit -> limit > 0).orElse(FIRST_PAGE));
    }

    private static long longOrZero(String text) {
        try {
            return Long.parseLong(text.strip());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static <T> Set<T> parseAll(List<String> texts, Function<String, T> parser) {
        var parsed = new ArrayList<T>();
        for (var text : texts) {
            try {
                parsed.add(parser.apply(text.strip()));
            } catch (IllegalArgumentException ignored) {
                // a value that does not parse filters nothing
            }
        }
        return Set.copyOf(parsed);
    }

    /** True when nothing narrows the list. */
    public boolean unfiltered() {
        return search.isEmpty() && categories.isEmpty() && groups.isEmpty() && characters.isEmpty() && places.isEmpty()
                && regions.isEmpty() && held.isEmpty() && minTotal == 0;
    }

    public BrowseFilter without(Dimension dimension) {
        return with(dimension, Set.of());
    }

    @SuppressWarnings("unchecked")
    BrowseFilter with(Dimension dimension, Set<?> values) {
        return new BrowseFilter(search,
                dimension == Dimension.CATEGORY ? (Set<Integer>) values : categories,
                dimension == Dimension.GROUP ? (Set<Integer>) values : groups,
                dimension == Dimension.CHARACTER ? (Set<UUID>) values : characters,
                dimension == Dimension.PLACE ? (Set<Long>) values : places,
                dimension == Dimension.REGION ? (Set<Integer>) values : regions,
                dimension == Dimension.HOW ? (Set<Held>) values : held,
                minTotal, sort, showCharacters, limit);
    }

    /** Leaves out what is fitted to a ship or in a ship's hold, which is not loose stock. */
    public BrowseFilter looseOnly() {
        return with(Dimension.HOW, Set.of(Held.HANGAR, Held.IN_CONTAINER, Held.DELIVERIES, Held.ASSET_SAFETY,
                Held.IMPOUNDED));
    }

    public BrowseFilter withMinimum(long minimum) {
        return new BrowseFilter(search, categories, groups, characters, places, regions, held, minimum, sort,
                showCharacters, limit);
    }

    BrowseFilter withoutMinimum() {
        return new BrowseFilter(search, categories, groups, characters, places, regions, held, 0, sort, showCharacters,
                limit);
    }

    public BrowseFilter withLimit(int newLimit) {
        return new BrowseFilter(search, categories, groups, characters, places, regions, held, minTotal, sort,
                showCharacters, newLimit);
    }

    /** The selected values of a column, as the text they travel in. */
    public List<String> valuesOf(Dimension dimension) {
        Collection<?> chosen = switch (dimension) {
            case CATEGORY -> categories;
            case GROUP -> groups;
            case CHARACTER -> characters;
            case PLACE -> places;
            case REGION -> regions;
            case HOW -> held;
        };
        return new TreeSet<>(chosen.stream().map(value -> value instanceof Held h ? h.name() : value.toString())
                .toList()).stream().toList();
    }

    /**
     * The filter as a query string: every part of it, in a steady order, so that an address can be copied, bookmarked
     * and compared. The first page is not written out, and {@code ui} marks that the form was sent.
     */
    public String toQuery() {
        var parts = new ArrayList<String>();
        parts.add("ui=1");
        if (!search.isEmpty()) {
            parts.add("q=" + encode(search));
        }
        for (var dimension : Dimension.values()) {
            valuesOf(dimension).forEach(value -> parts.add(dimension.parameter + "=" + encode(value)));
        }
        if (minTotal > 0) {
            parts.add("min=" + minTotal);
        }
        if (sort != Sort.TOTAL) {
            parts.add("sort=" + sort.name().toLowerCase(java.util.Locale.ROOT));
        }
        if (showCharacters) {
            parts.add("chars=1");
        }
        if (limit != FIRST_PAGE) {
            parts.add("limit=" + limit);
        }
        return String.join("&", parts);
    }

    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }
}
