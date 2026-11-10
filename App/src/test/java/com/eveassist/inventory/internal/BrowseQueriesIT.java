package com.eveassist.inventory.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.eveassist.IntegrationTest;
import com.eveassist.esi.CharacterAsset;
import com.eveassist.identity.Viewer;
import com.eveassist.inventory.internal.BrowseFilter.Dimension;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The inventory queries against real Postgres and the small SDE: totals, every filter, facets and places. */
class BrowseQueriesIT extends IntegrationTest {

    private static final long JITA = 60003760L;
    private static final long AMARR = 60008494L;
    private static final long STRUCTURE = 1_000_000_000_001L;
    private static final long UNKNOWN_STRUCTURE = 1_000_000_000_002L;
    private static final int TRITANIUM = 34;
    private static final int PILOTS_MINERAL = 35;
    private static final int HAULER = 1001;
    private static final int COMPONENT = 1002;
    private static final int HAULER_BLUEPRINT = 2001;

    @Autowired
    BrowseQueries queries;

    @Autowired
    AssetRepository assets;

    @Autowired
    JdbcClient jdbc;

    private UUID link(Viewer viewer, String name) {
        var id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO character (public_id, owner_account_id, eve_character_id, name, owner_hash, scopes, state)
                VALUES (:id, :owner, :eve, :name, 'hash', :scopes, 'ACTIVE')
                """)
                .param("id", id).param("owner", viewer.accountId().value())
                .param("eve", 2_000_000_000L + ThreadLocalRandom.current().nextInt(1_000_000_000)).param("name", name)
                .param("scopes", new String[] {"esi-assets.read_assets.v1"}).update();
        return id;
    }

    private static CharacterAsset asset(long itemId, int typeId, long quantity, long location, String flag) {
        return new CharacterAsset(itemId, typeId, quantity, location, "station", flag, false, null);
    }

    private void store(UUID character, CharacterAsset... items) {
        assets.replace(character, List.of(items), Instant.now());
    }

    private void nameStructure(UUID character, long structureId, String name, int system) {
        jdbc.sql("""
                INSERT INTO character_structure (character_id, structure_id, status, name, solar_system_id, asked_at)
                VALUES (:id, :s, 'FOUND', :name, :system, now())
                """)
                .param("id", character).param("s", structureId).param("name", name).param("system", system).update();
    }

    private UUID[] ids(UUID... ids) {
        return ids;
    }

    private static BrowseFilter filter() {
        return BrowseFilter.none();
    }

    private BrowseQueries.ItemRow row(List<BrowseQueries.ItemRow> rows, int typeId) {
        return rows.stream().filter(r -> r.typeId() == typeId).findFirst().orElseThrow();
    }

    @Test
    void totalsAnItemTypeAcrossCharactersWithTheBiggestHolderFirst() {
        var viewer = viewerFor(newSub());
        var one = link(viewer, "Alt One");
        var two = link(viewer, "Alt Two");
        store(one, asset(1, TRITANIUM, 100, JITA, "Hangar"), asset(2, TRITANIUM, 50, AMARR, "Hangar"));
        store(two, asset(1, TRITANIUM, 400, JITA, "Hangar"));

        var rows = queries.items(ids(one, two), filter());

        var tritanium = row(rows, TRITANIUM);
        assertThat(tritanium.total()).isEqualTo(550);
        assertThat(tritanium.holders()).containsExactly(two, one);
        assertThat(tritanium.held()).containsExactly(400L, 150L);
        assertThat(tritanium.places()).isEqualTo(2);
        assertThat(tritanium.name()).isEqualTo("Tritanium");
        assertThat(tritanium.groupName()).isEqualTo("Mineral");
        assertThat(tritanium.categoryName()).isEqualTo("Material");
        assertThat(queries.summary(ids(one, two), filter())).isEqualTo(new BrowseQueries.Summary(1, 3, 550));
    }

    @Test
    void neverShowsACharacterThatWasNotAskedFor() {
        var viewer = viewerFor(newSub());
        var mine = link(viewer, "Mine");
        var theirs = link(viewerFor(newSub()), "Theirs");
        store(mine, asset(1, TRITANIUM, 10, JITA, "Hangar"));
        store(theirs, asset(1, TRITANIUM, 9_999, JITA, "Hangar"), asset(2, HAULER, 1, JITA, "Hangar"));

        var rows = queries.items(ids(mine), filter());

        assertThat(rows).singleElement().satisfies(r -> assertThat(r.total()).isEqualTo(10));
        assertThat(queries.where(ids(mine), filter(), TRITANIUM)).singleElement()
                .satisfies(w -> assertThat(w.byCharacter()).containsOnlyKeys(mine));
        assertThat(queries.facet(ids(mine), Dimension.CHARACTER, filter())).extracting(BrowseQueries.Facet::value)
                .containsExactly(mine.toString());
        assertThat(queries.items(ids(), filter())).isEmpty();
    }

    @Test
    void eachFilterNarrowsTheListAndValuesInOneColumnMeanAnyOf() {
        var viewer = viewerFor(newSub());
        var one = link(viewer, "Alt One");
        var two = link(viewer, "Alt Two");
        store(one, asset(1, TRITANIUM, 100, JITA, "Hangar"), asset(2, HAULER, 1, AMARR, "Hangar"));
        store(two, asset(1, PILOTS_MINERAL, 20, JITA, "Hangar"), asset(2, COMPONENT, 5, AMARR, "Hangar"));
        var both = ids(one, two);

        // category: Material (4) is Tritanium and Pilot's Mineral
        assertThat(types(queries.items(both, filter().with(Dimension.CATEGORY, Set.of(4)))))
                .containsExactlyInAnyOrder(TRITANIUM, PILOTS_MINERAL);
        // any of two categories
        assertThat(types(queries.items(both, filter().with(Dimension.CATEGORY, Set.of(4, 6)))))
                .containsExactlyInAnyOrder(TRITANIUM, PILOTS_MINERAL, HAULER);
        // group: Component (2)
        assertThat(types(queries.items(both, filter().with(Dimension.GROUP, Set.of(2))))).containsExactly(COMPONENT);
        // character
        assertThat(types(queries.items(both, filter().with(Dimension.CHARACTER, Set.of(two)))))
                .containsExactlyInAnyOrder(PILOTS_MINERAL, COMPONENT);
        // place and region
        assertThat(types(queries.items(both, filter().with(Dimension.PLACE, Set.of(AMARR)))))
                .containsExactlyInAnyOrder(HAULER, COMPONENT);
        assertThat(types(queries.items(both, filter().with(Dimension.REGION, Set.of(10000002)))))
                .containsExactlyInAnyOrder(TRITANIUM, PILOTS_MINERAL);
        // the minimum is on the total of the item type
        assertThat(types(queries.items(both, filter().with(Dimension.CATEGORY, Set.of(4)).withMinimum(50))))
                .containsExactly(TRITANIUM);
    }

    @Test
    void filtersFromDifferentColumnsAllHaveToHold() {
        var viewer = viewerFor(newSub());
        var one = link(viewer, "Alt One");
        var two = link(viewer, "Alt Two");
        store(one, asset(1, TRITANIUM, 100, JITA, "Hangar"), asset(2, TRITANIUM, 10, AMARR, "Hangar"));
        store(two, asset(1, TRITANIUM, 400, JITA, "Hangar"), asset(2, HAULER, 1, JITA, "Hangar"));
        var both = ids(one, two);

        var narrowed = filter().with(Dimension.CATEGORY, Set.of(4)).with(Dimension.CHARACTER, Set.of(one))
                .with(Dimension.PLACE, Set.of(AMARR));
        var rows = queries.items(both, narrowed);

        // only Alt One's Tritanium in Amarr is left: 10, not the 510 in all
        assertThat(rows).singleElement().satisfies(r -> {
            assertThat(r.typeId()).isEqualTo(TRITANIUM);
            assertThat(r.total()).isEqualTo(10);
            assertThat(r.holders()).containsExactly(one);
        });
        assertThat(queries.summary(both, narrowed)).isEqualTo(new BrowseQueries.Summary(1, 1, 10));
    }

    @Test
    void heldFiltersLeaveOutFittedModulesAndShipHolds() {
        var viewer = viewerFor(newSub());
        var one = link(viewer, "Alt One");
        store(one,
                asset(1, HAULER, 1, JITA, "Hangar"),
                new CharacterAsset(2, COMPONENT, 3, 1, "item", "HiSlot0", false, null),
                new CharacterAsset(3, COMPONENT, 4, 1, "item", "Cargo", false, null),
                new CharacterAsset(4, COMPONENT, 5, 1, "item", "Unlocked", false, null),
                asset(5, COMPONENT, 6, JITA, "AssetSafety"));

        var all = queries.items(ids(one), filter());
        var loose = queries.items(ids(one),
                filter().with(Dimension.HOW, Set.of(Held.HANGAR, Held.IN_CONTAINER, Held.ASSET_SAFETY)));
        var fitted = queries.items(ids(one), filter().with(Dimension.HOW, Set.of(Held.FITTED)));

        assertThat(row(all, COMPONENT).total()).isEqualTo(18);
        assertThat(row(loose, COMPONENT).total()).isEqualTo(11);
        assertThat(row(fitted, COMPONENT).total()).isEqualTo(3);
        assertThat(types(fitted)).containsExactly(COMPONENT);
    }

    @Test
    void searchMatchesItemGroupAndCategoryAndTreatsSpecialCharactersAsText() {
        var viewer = viewerFor(newSub());
        var one = link(viewer, "Alt One");
        store(one, asset(1, TRITANIUM, 1, JITA, "Hangar"), asset(2, HAULER, 1, JITA, "Hangar"),
                asset(3, PILOTS_MINERAL, 1, JITA, "Hangar"));

        assertThat(types(queries.items(ids(one), search("TRIT")))).containsExactly(TRITANIUM);
        assertThat(types(queries.items(ids(one), search("mineral")))).containsExactlyInAnyOrder(TRITANIUM, PILOTS_MINERAL);
        assertThat(types(queries.items(ids(one), search("ship")))).containsExactly(HAULER);
        assertThat(types(queries.items(ids(one), search("%")))).isEmpty();
        assertThat(types(queries.items(ids(one), search("_ritanium")))).isEmpty();
        assertThat(types(queries.items(ids(one), search("pilot's")))).containsExactly(PILOTS_MINERAL);
        assertThat(types(queries.items(ids(one), search("x'; DROP TABLE character_asset; --")))).isEmpty();
        assertThat(types(queries.items(ids(one), filter()))).hasSize(3);
    }

    private static BrowseFilter search(String text) {
        return new BrowseFilter(text, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), 0,
                BrowseFilter.Sort.TOTAL, true, BrowseFilter.FIRST_PAGE);
    }

    @Test
    void sortsByUnitsNameOrCategoryAndSummarisesBeforeThePageLimit() {
        var viewer = viewerFor(newSub());
        var one = link(viewer, "Alt One");
        store(one, asset(1, TRITANIUM, 500, JITA, "Hangar"), asset(2, HAULER, 1, JITA, "Hangar"),
                asset(3, PILOTS_MINERAL, 20, JITA, "Hangar"), asset(4, COMPONENT, 9, JITA, "Hangar"));
        var ids = ids(one);

        assertThat(types(queries.items(ids, sorted(BrowseFilter.Sort.TOTAL, 100)))).containsExactly(TRITANIUM,
                PILOTS_MINERAL, COMPONENT, HAULER);
        // "Pilot's Mineral", "Test Component", "Test Hauler", "Tritanium"
        assertThat(types(queries.items(ids, sorted(BrowseFilter.Sort.NAME, 100)))).containsExactly(PILOTS_MINERAL,
                COMPONENT, HAULER, TRITANIUM);
        // Material, Commodity, Ship: by category name
        assertThat(types(queries.items(ids, sorted(BrowseFilter.Sort.CATEGORY, 100)))).containsExactly(COMPONENT,
                PILOTS_MINERAL, TRITANIUM, HAULER);
        var firstTwo = sorted(BrowseFilter.Sort.TOTAL, 2);
        assertThat(queries.items(ids, firstTwo)).hasSize(2);
        assertThat(queries.summary(ids, firstTwo).itemTypes()).isEqualTo(4);
    }

    private static BrowseFilter sorted(BrowseFilter.Sort sort, int limit) {
        return new BrowseFilter("", Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), 0, sort, true, limit);
    }

    @Test
    void placesAreNamedFromTheSdeAndFromStructuresAnyCharacterOfTheAccountNamed() {
        var viewer = viewerFor(newSub());
        var one = link(viewer, "Alt One");
        var two = link(viewer, "Alt Two");
        store(one, asset(1, TRITANIUM, 10, JITA, "Hangar"),
                new CharacterAsset(2, TRITANIUM, 20, STRUCTURE, "item", "Hangar", false, null),
                new CharacterAsset(3, TRITANIUM, 30, UNKNOWN_STRUCTURE, "item", "Hangar", false, null),
                new CharacterAsset(4, TRITANIUM, 40, 30000142L, "solar_system", "Unlocked", false, null));
        // the second character, not the first, learned the structure's name
        nameStructure(two, STRUCTURE, "Alpha Keepstar", 30002187);

        var where = queries.where(ids(one, two), filter(), TRITANIUM);

        assertThat(where).extracting(BrowseQueries.WhereRow::placeName).containsExactlyInAnyOrder(
                "Jita IV - Moon 4 - Caldari Navy Assembly Plant", "Alpha Keepstar", null, "Jita");
        assertThat(where).filteredOn(w -> w.locationId() == STRUCTURE).singleElement().satisfies(w -> {
            assertThat(w.placeKind()).isEqualTo("STRUCTURE");
            // the structure is in Amarr's system, which is in Domain, so the region comes from the name's system
            assertThat(w.regionName()).isEqualTo("Domain");
        });
        assertThat(where).filteredOn(w -> w.locationId() == UNKNOWN_STRUCTURE).singleElement().satisfies(w -> {
            assertThat(w.placeKind()).isEqualTo("UNKNOWN_STRUCTURE");
            assertThat(w.regionName()).isNull();
        });
        assertThat(where).filteredOn(w -> w.locationId() == 30000142L).singleElement()
                .satisfies(w -> assertThat(w.placeKind()).isEqualTo("SYSTEM"));
        // the structure's region is also what the region filter sees
        assertThat(types(queries.items(ids(one, two), filter().with(Dimension.REGION, Set.of(10000043))))).containsExactly(TRITANIUM);
        assertThat(queries.items(ids(one, two), filter().with(Dimension.REGION, Set.of(10000043))).getFirst().total())
                .isEqualTo(20);
    }

    @Test
    void aStructureNameLearnedByAnotherAccountIsNeverUsed() {
        var viewer = viewerFor(newSub());
        var mine = link(viewer, "Mine");
        var theirs = link(viewerFor(newSub()), "Theirs");
        store(mine, new CharacterAsset(1, TRITANIUM, 5, STRUCTURE, "item", "Hangar", false, null));
        nameStructure(theirs, STRUCTURE, "Someone Else's Keepstar", 30000142);

        var where = queries.where(ids(mine), filter(), TRITANIUM);

        assertThat(where).singleElement().satisfies(w -> assertThat(w.placeName()).isNull());
    }

    @Test
    void groupsWhereByPlaceAndHowAndAddsTheCharactersUp() {
        var viewer = viewerFor(newSub());
        var one = link(viewer, "Alt One");
        var two = link(viewer, "Alt Two");
        store(one, asset(1, TRITANIUM, 10, JITA, "Hangar"), asset(2, TRITANIUM, 5, JITA, "Hangar"),
                asset(3, HAULER, 1, JITA, "Hangar"), new CharacterAsset(4, TRITANIUM, 7, 3, "item", "Cargo", false, null));
        store(two, asset(1, TRITANIUM, 100, JITA, "Hangar"));

        var where = queries.where(ids(one, two), filter(), TRITANIUM);

        assertThat(where).hasSize(2);
        assertThat(where.getFirst().held()).isEqualTo(Held.HANGAR);
        assertThat(where.getFirst().total()).isEqualTo(115);
        assertThat(where.getFirst().byCharacter()).containsEntry(one, 15L).containsEntry(two, 100L);
        assertThat(where.get(1).held()).isEqualTo(Held.IN_SHIP);
        assertThat(where.get(1).total()).isEqualTo(7);
    }

    @Test
    void facetsCountItemTypesAndLeaveOutTheColumnsOwnFilter() {
        var viewer = viewerFor(newSub());
        var one = link(viewer, "Alt One");
        var two = link(viewer, "Alt Two");
        store(one, asset(1, TRITANIUM, 10, JITA, "Hangar"), asset(2, HAULER, 1, AMARR, "Hangar"));
        store(two, asset(1, PILOTS_MINERAL, 20, JITA, "Hangar"), asset(2, COMPONENT, 5, JITA, "Hangar"));
        var both = ids(one, two);

        var categories = queries.facet(both, Dimension.CATEGORY, filter());
        assertThat(categories).extracting(BrowseQueries.Facet::label, BrowseQueries.Facet::itemTypes).containsExactly(
                org.assertj.core.groups.Tuple.tuple("Commodity", 1), org.assertj.core.groups.Tuple.tuple("Material", 2),
                org.assertj.core.groups.Tuple.tuple("Ship", 1));

        // with Alt One chosen, the categories shrink; the character column still offers both characters
        var chosenOne = filter().with(Dimension.CHARACTER, Set.of(one));
        assertThat(queries.facet(both, Dimension.CATEGORY, chosenOne)).extracting(BrowseQueries.Facet::label)
                .containsExactly("Material", "Ship");
        assertThat(queries.facet(both, Dimension.CHARACTER, chosenOne)).extracting(BrowseQueries.Facet::value)
                .containsExactlyInAnyOrder(one.toString(), two.toString());

        var places = queries.facet(both, Dimension.PLACE, filter());
        assertThat(places).extracting(BrowseQueries.Facet::value).containsExactlyInAnyOrder(
                String.valueOf(JITA), String.valueOf(AMARR));
        assertThat(queries.facet(both, Dimension.REGION, filter())).extracting(BrowseQueries.Facet::label)
                .containsExactly("Domain", "The Forge");
        assertThat(queries.facet(both, Dimension.HOW, filter())).extracting(BrowseQueries.Facet::value)
                .containsExactly("HANGAR");
        assertThat(queries.facet(both, Dimension.GROUP, filter())).hasSize(3);
    }

    @Test
    void flagsBlueprintCopiesAndOriginals() {
        var viewer = viewerFor(newSub());
        var one = link(viewer, "Alt One");
        store(one,
                new CharacterAsset(1, HAULER_BLUEPRINT, 1, JITA, "station", "Hangar", true, true),
                new CharacterAsset(2, 2002, 1, JITA, "station", "Hangar", true, null),
                new CharacterAsset(3, TRITANIUM, 5, JITA, "station", "Hangar", false, null));

        var rows = queries.items(ids(one), filter());

        assertThat(row(rows, HAULER_BLUEPRINT).hasBlueprintCopy()).isTrue();
        assertThat(row(rows, HAULER_BLUEPRINT).hasBlueprintOriginal()).isFalse();
        assertThat(row(rows, 2002).hasBlueprintCopy()).isFalse();
        assertThat(row(rows, 2002).hasBlueprintOriginal()).isTrue();
        assertThat(row(rows, TRITANIUM).hasBlueprintCopy()).isFalse();
        assertThat(row(rows, TRITANIUM).hasBlueprintOriginal()).isFalse();
    }

    @Test
    void labelsNameTheChosenValuesForTheChips() {
        var viewer = viewerFor(newSub());
        var one = link(viewer, "Alt One");
        store(one, asset(1, TRITANIUM, 1, JITA, "Hangar"));

        assertThat(queries.labels(ids(one), Dimension.CATEGORY, List.of("4", "6"))).containsEntry("4", "Material")
                .containsEntry("6", "Ship");
        assertThat(queries.labels(ids(one), Dimension.GROUP, List.of("3"))).containsEntry("3", "Mineral");
        assertThat(queries.labels(ids(one), Dimension.REGION, List.of("10000002"))).containsEntry("10000002", "The Forge");
        assertThat(queries.labels(ids(one), Dimension.PLACE, List.of(String.valueOf(JITA))))
                .containsEntry(String.valueOf(JITA), "Jita IV - Moon 4 - Caldari Navy Assembly Plant");
        assertThat(queries.labels(ids(one), Dimension.CATEGORY, List.of())).isEmpty();
    }

    @Test
    void exportsOneLinePerItemCharacterPlaceAndWayOfHolding() {
        var viewer = viewerFor(newSub());
        var one = link(viewer, "Alt One");
        var two = link(viewer, "Alt Two");
        store(one, asset(1, TRITANIUM, 10, JITA, "Hangar"), asset(2, TRITANIUM, 5, JITA, "Hangar"));
        store(two, asset(1, TRITANIUM, 100, AMARR, "Hangar"));

        var lines = queries.export(ids(one, two), filter().with(Dimension.CHARACTER, Set.of(one, two)));

        assertThat(lines).hasSize(2);
        assertThat(lines).extracting(BrowseQueries.ExportRow::quantity).containsExactlyInAnyOrder(15L, 100L);
        assertThat(lines).extracting(BrowseQueries.ExportRow::item).containsOnly("Tritanium");
        assertThat(queries.export(ids(one, two), filter().with(Dimension.PLACE, Set.of(AMARR)))).singleElement()
                .satisfies(l -> assertThat(l.character()).isEqualTo(two));
    }

    @Test
    void theExportHonoursTheMinimumOnAnItemTypesTotal() {
        var viewer = viewerFor(newSub());
        var one = link(viewer, "Alt One");
        var two = link(viewer, "Alt Two");
        store(one, asset(1, TRITANIUM, 300, JITA, "Hangar"), asset(2, PILOTS_MINERAL, 20, JITA, "Hangar"));
        store(two, asset(1, TRITANIUM, 300, AMARR, "Hangar"), asset(2, PILOTS_MINERAL, 30, AMARR, "Hangar"));
        var both = ids(one, two);

        // Tritanium is 600 in all and Pilot's Mineral 50: a minimum of 100 keeps every Tritanium line, from both characters
        var lines = queries.export(both, filter().withMinimum(100));

        assertThat(lines).extracting(BrowseQueries.ExportRow::item).containsOnly("Tritanium");
        assertThat(lines).hasSize(2);
        assertThat(queries.export(both, filter().withMinimum(10_000))).isEmpty();
        assertThat(queries.export(both, filter())).hasSize(4);
        assertThat(queries.typesAtLeastMinimum(both, filter().withMinimum(100))).containsExactly(TRITANIUM);
    }

    @Test
    void theListsOfValuesCountOnlyTheItemTypesThatReachTheMinimum() {
        var viewer = viewerFor(newSub());
        var one = link(viewer, "Alt One");
        store(one, asset(1, TRITANIUM, 600, JITA, "Hangar"), asset(2, HAULER, 1, AMARR, "Hangar"));
        var both = ids(one);

        var without = queries.facet(both, Dimension.CATEGORY, filter());
        var withMinimum = queries.facet(both, Dimension.CATEGORY, filter().withMinimum(100));

        assertThat(without).extracting(BrowseQueries.Facet::label).containsExactly("Material", "Ship");
        assertThat(withMinimum).extracting(BrowseQueries.Facet::label).containsExactly("Material");
        assertThat(queries.facet(both, Dimension.CATEGORY, filter().withMinimum(100_000))).isEmpty();
    }

    @Test
    void aListOfValuesCanBeSearchedInTheDatabaseSoALongListStaysReachable() {
        var viewer = viewerFor(newSub());
        var one = link(viewer, "Alt One");
        store(one, asset(1, TRITANIUM, 1, JITA, "Hangar"), asset(2, HAULER, 1, AMARR, "Hangar"));

        var searched = queries.facet(ids(one), Dimension.PLACE, filter(), "amarr");

        assertThat(searched).extracting(BrowseQueries.Facet::value).containsExactly(String.valueOf(AMARR));
        assertThat(queries.facet(ids(one), Dimension.PLACE, filter(), "%")).isEmpty();
        assertThat(queries.facet(ids(one), Dimension.PLACE, filter(), "")).hasSize(2);
        assertThat(queries.facet(ids(one), Dimension.PLACE, filter(), null)).hasSize(2);
    }

    @Test
    void theCopyOfTheWholeListIsNotLimitedByThePageSize() {
        var viewer = viewerFor(newSub());
        var one = link(viewer, "Alt One");
        store(one, asset(1, TRITANIUM, 5, JITA, "Hangar"), asset(2, HAULER, 1, JITA, "Hangar"),
                asset(3, PILOTS_MINERAL, 3, JITA, "Hangar"));

        assertThat(queries.items(ids(one), sorted(BrowseFilter.Sort.TOTAL, 2))).hasSize(2);
        assertThat(queries.items(ids(one), sorted(BrowseFilter.Sort.TOTAL, 2), 100)).hasSize(3);
    }

    private static List<Integer> types(List<BrowseQueries.ItemRow> rows) {
        return rows.stream().map(BrowseQueries.ItemRow::typeId).toList();
    }
}
