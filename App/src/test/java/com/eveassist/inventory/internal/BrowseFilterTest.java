package com.eveassist.inventory.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.eveassist.inventory.internal.BrowseFilter.Dimension;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BrowseFilterTest {

    private static Map<String, String[]> params(String... pairs) {
        var map = new java.util.LinkedHashMap<String, java.util.List<String>>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.computeIfAbsent(pairs[i], key -> new java.util.ArrayList<>()).add(pairs[i + 1]);
        }
        var result = new java.util.LinkedHashMap<String, String[]>();
        map.forEach((key, list) -> result.put(key, list.toArray(String[]::new)));
        return result;
    }

    @Test
    void anEmptyAddressListsEverythingWithCharactersShown() {
        var filter = BrowseFilter.parse(Map.of());

        assertThat(filter.unfiltered()).isTrue();
        assertThat(filter.showCharacters()).isTrue();
        assertThat(filter.sort()).isEqualTo(BrowseFilter.Sort.TOTAL);
        assertThat(filter.limit()).isEqualTo(BrowseFilter.FIRST_PAGE);
    }

    @Test
    void valuesInOneColumnAreKeptTogetherAndColumnsAreKeptApart() {
        var one = UUID.randomUUID();
        var filter = BrowseFilter.parse(params("category", "6", "category", "7", "group", "25", "character", one.toString(),
                "place", "60003760", "region", "10000002", "how", "fitted", "how", "IN_SHIP", "min", "1000",
                "q", " tritanium ", "sort", "name", "ui", "1", "chars", "1"));

        assertThat(filter.categories()).containsExactlyInAnyOrder(6, 7);
        assertThat(filter.groups()).containsExactly(25);
        assertThat(filter.characters()).containsExactly(one);
        assertThat(filter.places()).containsExactly(60003760L);
        assertThat(filter.regions()).containsExactly(10000002);
        assertThat(filter.held()).containsExactlyInAnyOrder(Held.FITTED, Held.IN_SHIP);
        assertThat(filter.minTotal()).isEqualTo(1000);
        assertThat(filter.search()).isEqualTo("tritanium");
        assertThat(filter.sort()).isEqualTo(BrowseFilter.Sort.NAME);
        assertThat(filter.unfiltered()).isFalse();
    }

    @Test
    void valuesThatDoNotParseAreDroppedAndNothingFails() {
        var filter = BrowseFilter.parse(params("category", "x", "category", "6", "character", "not-a-uuid", "place", "",
                "how", "nonsense", "min", "-5", "sort", "bogus", "limit", "abc"));

        assertThat(filter.categories()).containsExactly(6);
        assertThat(filter.characters()).isEmpty();
        assertThat(filter.places()).isEmpty();
        assertThat(filter.held()).isEmpty();
        assertThat(filter.minTotal()).isZero();
        assertThat(filter.sort()).isEqualTo(BrowseFilter.Sort.TOTAL);
        assertThat(filter.limit()).isEqualTo(BrowseFilter.FIRST_PAGE);
    }

    @Test
    void theSizesAreCappedBecauseTheWholeFilterComesFromTheAddress() {
        var many = new String[500];
        for (int i = 0; i < many.length; i++) {
            many[i] = String.valueOf(i);
        }
        var map = new java.util.HashMap<String, String[]>();
        map.put("category", many);
        map.put("q", new String[] {"x".repeat(1_000)});
        map.put("limit", new String[] {"999999"});

        var filter = BrowseFilter.parse(map);

        assertThat(filter.categories()).hasSize(200);
        assertThat(filter.search()).hasSize(100);
        assertThat(filter.limit()).isEqualTo(BrowseFilter.MOST_ITEMS);
    }

    @Test
    void charactersAreHiddenOnlyWhenTheFormWasSentWithoutTheirBox() {
        assertThat(BrowseFilter.parse(params("ui", "1")).showCharacters()).isFalse();
        assertThat(BrowseFilter.parse(params("ui", "1", "chars", "1")).showCharacters()).isTrue();
        assertThat(BrowseFilter.parse(params("category", "6")).showCharacters()).isTrue();
    }

    @Test
    void theQueryStringRoundTripsAndIsSteady() {
        var one = UUID.fromString("11111111-1111-1111-1111-111111111111");
        var filter = BrowseFilter.parse(params("category", "7", "category", "6", "character", one.toString(),
                "how", "fitted", "q", "a&b c", "min", "5", "sort", "category", "ui", "1", "chars", "1"));

        var query = filter.toQuery();

        assertThat(query).isEqualTo("ui=1&q=a%26b+c&category=6&category=7&character=" + one + "&how=FITTED&min=5"
                + "&sort=category&chars=1");
        assertThat(roundTrip(query)).isEqualTo(query);
    }

    private static String roundTrip(String query) {
        var map = new java.util.LinkedHashMap<String, java.util.List<String>>();
        for (var part : query.split("&")) {
            var pair = part.split("=", 2);
            map.computeIfAbsent(pair[0], key -> new java.util.ArrayList<>())
                    .add(java.net.URLDecoder.decode(pair[1], java.nio.charset.StandardCharsets.UTF_8));
        }
        var result = new java.util.LinkedHashMap<String, String[]>();
        map.forEach((key, list) -> result.put(key, list.toArray(String[]::new)));
        return BrowseFilter.parse(result).toQuery();
    }

    @Test
    void takingAColumnOrTheMinimumAwayLeavesTheRest() {
        var filter = BrowseFilter.parse(params("category", "6", "group", "25", "min", "10", "q", "ore"));

        var withoutCategory = filter.without(Dimension.CATEGORY);
        assertThat(withoutCategory.categories()).isEmpty();
        assertThat(withoutCategory.groups()).containsExactly(25);
        assertThat(withoutCategory.minTotal()).isEqualTo(10);
        assertThat(filter.withoutMinimum().minTotal()).isZero();
        assertThat(filter.withoutMinimum().search()).isEqualTo("ore");
        assertThat(filter.with(Dimension.GROUP, Set.of(1, 2)).groups()).containsExactlyInAnyOrder(1, 2);
    }
}
