package com.eveassist.inventory.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eveassist.IntegrationTest;
import com.eveassist.characters.Feature;
import com.eveassist.esi.CharacterAsset;
import com.eveassist.esi.CharacterBlueprint;
import com.eveassist.identity.Viewer;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Character and corporation blueprint data on the page, including its access boundaries and detail drawer. */
@AutoConfigureMockMvc
class BlueprintsPageIT extends IntegrationTest {

    private static final String ASSETS_SCOPE = "esi-assets.read_assets.v1";
    private static final String BLUEPRINT_SCOPE = "esi-characters.read_blueprints.v1";
    private static final String CORP_ASSETS_SCOPE = "esi-assets.read_corporation_assets.v1";
    private static final String CORP_DIVISIONS_SCOPE = "esi-corporations.read_divisions.v1";
    private static final String CORP_ROLES_SCOPE = "esi-characters.read_corporation_roles.v1";
    private static final String CORP_BLUEPRINT_SCOPE = "esi-corporations.read_blueprints.v1";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    BlueprintFetcher fetcher;

    @Autowired
    CorporationFetcher corporationFetcher;

    @Autowired
    BlueprintRepository blueprintRepository;

    private RequestPostProcessor signedInAs(String sub) {
        return oidcLogin().idToken(token -> token.subject(sub)).authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private record Character(UUID id, long eveId) {
    }

    private Character link(Viewer viewer, String name, String... scopes) {
        var character = new Character(UUID.randomUUID(), 2_000_000_000L + ThreadLocalRandom.current().nextInt(1_000_000_000));
        jdbc.sql("""
                INSERT INTO character (public_id, owner_account_id, eve_character_id, name, owner_hash, scopes, state)
                VALUES (:id, :owner, :eve, :name, 'hash', :scopes, 'ACTIVE')
                """)
                .param("id", character.id()).param("owner", viewer.accountId().value()).param("eve", character.eveId())
                .param("name", name).param("scopes", scopes).update();
        return character;
    }

    private void turnOn(Viewer viewer, Feature... features) {
        for (var feature : features) {
            jdbc.sql("INSERT INTO account_feature (owner_account_id, feature) VALUES (:owner, :feature) ON CONFLICT DO NOTHING")
                    .param("owner", viewer.accountId().value()).param("feature", feature.name()).update();
        }
    }

    private void startAndWait(String sub, Viewer viewer) throws Exception {
        mvc.perform(get("/blueprints/status").param("start", "1").with(signedInAs(sub)))
                .andExpect(status().isOk());
        await().atMost(10, TimeUnit.SECONDS).until(() -> !fetcher.anyRunning(viewer));
    }

    private static CharacterAsset asset(long itemId, int typeId, long locationId, String locationType,
            String locationFlag, Boolean blueprintCopy) {
        return new CharacterAsset(itemId, typeId, 1, locationId, locationType, locationFlag, true, blueprintCopy);
    }

    @Test
    void showsCharacterBlueprintsWithFiltersPlaceAndRecipeDetail() throws Exception {
        var sub = newSub();
        var viewer = viewerFor(sub);
        turnOn(viewer, Feature.INVENTORY, Feature.BLUEPRINTS);
        var character = link(viewer, "Test Character", ASSETS_SCOPE, BLUEPRINT_SCOPE);
        esi.assetsOf(character.eveId(), List.of(asset(81001, 2001, 60003760L, "station", "Hangar", false),
                asset(81003, 2002, 60008494L, "station", "Hangar", true)));
        esi.blueprintsOf(character.eveId(), List.of(
                new CharacterBlueprint(81001, 2001, 60003760L, "Hangar", -1, 4, 8, -1),
                new CharacterBlueprint(81002, 2002, 60008494L, "Hangar", -2, 9, 18, 12),
                new CharacterBlueprint(81003, 2002, 60008494L, "Hangar", -2, 0, 0, 4)));

        startAndWait(sub, viewer);

        var page = mvc.perform(get("/blueprints").with(signedInAs(sub))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(page).contains("Test Hauler Blueprint", "Test Component Blueprint", "Jita IV - Moon 4 - Caldari Navy Assembly Plant",
                "not matched yet", "Under development", "ME");
        // the results block is part of the page once, inside main, and not again after the layout
        assertThat(page.split("id=\"blueprint-actions\"", -1)).hasSize(2);
        var inventory = mvc.perform(get("/inventory").with(signedInAs(sub))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(inventory).contains("/blueprints?type=2001");

        mvc.perform(get("/blueprints").param("kind", "BPC").param("me", "5").with(signedInAs(sub)))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Test Component Blueprint")))
                .andExpect(content().string(not(containsString("Test Hauler Blueprint"))))
                .andExpect(content().string(not(containsString("data-blueprint-row=\"81003\""))));
        mvc.perform(get("/blueprints").param("type", "2001").with(signedInAs(sub)))
                .andExpect(content().string(containsString("Test Hauler Blueprint")))
                .andExpect(content().string(not(containsString("data-blueprint-row=\"81002\""))));

        var detail = mvc.perform(get("/blueprints/81001").with(signedInAs(sub))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(detail).contains("One run makes 1 Test Hauler", "At this ME (4)", "At ME 10", "Test Component", "Tritanium")
                .contains("Build time:");

        var csv = mvc.perform(post("/blueprints/export.csv").param("item", "81001").with(csrf()).with(signedInAs(sub)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(csv).contains("\"Test Hauler Blueprint\",\"BPO\",\"Under development\",\"4\",\"8\"");
    }

    @Test
    void thePageStartsTheReadOnceAndThePollNeverRestartsIt() throws Exception {
        var sub = newSub();
        var viewer = viewerFor(sub);
        turnOn(viewer, Feature.BLUEPRINTS);
        var character = link(viewer, "Polling Character", BLUEPRINT_SCOPE);
        esi.blueprintsOf(character.eveId(), List.of(new CharacterBlueprint(87001, 2001, 60003760L, "Hangar", -1, 4, 8, -1)));

        var page = mvc.perform(get("/blueprints").with(signedInAs(sub))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(page).contains("hx-get=\"/blueprints/status?start=1\"").contains("hx-trigger=\"load\"");

        // what the status block asks for after that must carry no start, not even an empty one
        var poll = mvc.perform(get("/blueprints/status").param("start", "1").with(signedInAs(sub)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(poll).doesNotContain("start=");
        await().atMost(10, TimeUnit.SECONDS).until(() -> !fetcher.anyRunning(viewer));
        var asked = esi.blueprintsAsked().size();
        assertThat(asked).isEqualTo(1);

        mvc.perform(get("/blueprints/status").param("start", "").with(signedInAs(sub))).andExpect(status().isOk());
        mvc.perform(get("/blueprints/status").with(signedInAs(sub))).andExpect(status().isOk());
        Thread.sleep(500);
        assertThat(esi.blueprintsAsked()).hasSize(asked);
    }

    /** Three corporations: one with no Director, one whose Director lacks the blueprint scope, one that can be read. */
    private long[] threeCorporations(Viewer viewer) {
        var plain = 98_400_001L + ThreadLocalRandom.current().nextInt(100_000);
        var unscoped = plain + 1_000_000L;
        var good = plain + 2_000_000L;
        esi.corporationNamed(plain, "Plain Corporation");
        esi.corporationNamed(unscoped, "Unscoped Corporation");
        esi.corporationNamed(good, "Readable Corporation");
        esi.hangars(good, java.util.Map.of(2, "Blueprint Hangar"));
        esi.corporationAssetsOf(good, List.of(asset(88000, 27, 60003760L, "station", "OfficeFolder", null)));
        esi.corporationBlueprintsOf(good, List.of(new CharacterBlueprint(88001, 2001, 88000L, "CorpSAG2", -1, 10, 20, -1)));
        var member = link(viewer, "Plain Member", ASSETS_SCOPE, BLUEPRINT_SCOPE, CORP_ROLES_SCOPE);
        esi.inCorporation(member.eveId(), plain);
        var withoutScope = link(viewer, "Unscoped Director", ASSETS_SCOPE, BLUEPRINT_SCOPE, CORP_ASSETS_SCOPE,
                CORP_DIVISIONS_SCOPE, CORP_ROLES_SCOPE);
        esi.inCorporation(withoutScope.eveId(), unscoped, "Director");
        var director = link(viewer, "Readable Director", ASSETS_SCOPE, BLUEPRINT_SCOPE, CORP_ASSETS_SCOPE,
                CORP_DIVISIONS_SCOPE, CORP_ROLES_SCOPE, CORP_BLUEPRINT_SCOPE);
        esi.inCorporation(director.eveId(), good, "Director");
        return new long[] {plain, unscoped, good};
    }

    @Test
    void corporationsWithoutAReadableDirectorAreNotShownNorAskedAbout() throws Exception {
        var sub = newSub();
        var viewer = viewerFor(sub);
        turnOn(viewer, Feature.INVENTORY, Feature.BLUEPRINTS, Feature.CORPORATION_ASSETS, Feature.CORPORATION_BLUEPRINTS);
        var corporations = threeCorporations(viewer);

        startAndWait(sub, viewer);

        // ESI was asked about the corporation that can be read and about no other
        assertThat(esi.corporationNamesAsked()).containsExactly(corporations[2]);
        assertThat(esi.corporationBlueprintsAsked()).hasSize(1).allMatch(asked -> asked.startsWith(corporations[2] + ":"));
        for (var url : List.of("/blueprints", "/blueprints/status")) {
            var body = mvc.perform(get(url).with(signedInAs(sub))).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).contains("Readable Corporation")
                    .doesNotContain("Plain Corporation", "Unscoped Corporation", "No Director linked",
                            "Director needs authorization");
        }
    }

    @Test
    void corporationsTheInventoryPageLearnedStayHiddenOnTheBlueprintsPage() throws Exception {
        var sub = newSub();
        var viewer = viewerFor(sub);
        turnOn(viewer, Feature.INVENTORY, Feature.BLUEPRINTS, Feature.CORPORATION_ASSETS, Feature.CORPORATION_BLUEPRINTS);
        var corporations = threeCorporations(viewer);

        // the Inventory page names every corporation its characters are in, whether or not one can be read
        mvc.perform(get("/inventory/status").param("start", "1").with(signedInAs(sub))).andExpect(status().isOk());
        await().atMost(10, TimeUnit.SECONDS).until(() -> !corporationFetcher.anyRunning(viewer));
        assertThat(esi.corporationNamesAsked()).contains(corporations[0], corporations[1]);

        startAndWait(sub, viewer);
        var page = mvc.perform(get("/blueprints").with(signedInAs(sub))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(page).contains("Readable Corporation", "Test Hauler Blueprint")
                .doesNotContain("Plain Corporation", "Unscoped Corporation", "No Director linked");
        // nor in the Holder filter, which would let a viewer pick a corporation with nothing to show
        var holders = page.substring(page.indexOf("name=\"holder\""));
        holders = holders.substring(0, holders.indexOf("</select>"));
        assertThat(holders).contains("Readable Corporation", "Readable Director").doesNotContain("Plain Corporation",
                "Unscoped Corporation");
    }

    private static List<CharacterBlueprint> manyBlueprints(int count) {
        var list = new java.util.ArrayList<CharacterBlueprint>();
        for (var i = 0; i < count; i++) {
            list.add(new CharacterBlueprint(90_000L + i, i % 2 == 0 ? 2001 : 2002, 60003760L, "Hangar", -1, i % 11, i % 21, -1));
        }
        return list;
    }

    private int rowsIn(String page) {
        return page.split("data-blueprint-row=", -1).length - 1;
    }

    @Test
    void listsOnePageAtATimeAndStopsAtTheMostLikeInventory() throws Exception {
        var sub = newSub();
        var viewer = viewerFor(sub);
        turnOn(viewer, Feature.BLUEPRINTS);
        var character = link(viewer, "Paging Character", BLUEPRINT_SCOPE);
        esi.blueprintsOf(character.eveId(), manyBlueprints(1_150));
        startAndWait(sub, viewer);

        var first = mvc.perform(get("/blueprints").with(signedInAs(sub))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(rowsIn(first)).isEqualTo(100);
        assertThat(first).contains("Showing 100 of 1,150 blueprints", "id=\"blueprints-show-more\"", "limit=200")
                .doesNotContain("blueprints-capped");
        // a new sort order starts again at the first page, and a blueprint's drawer returns to the page size it had
        assertThat(first).contains("sort=me&amp;dir=asc\"").doesNotContain("sort=me&amp;dir=asc&amp;limit");

        var more = mvc.perform(get("/blueprints").param("limit", "300").with(signedInAs(sub))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(rowsIn(more)).isEqualTo(300);
        assertThat(more).contains("Showing 300 of 1,150 blueprints", "limit=400");
        var drawer = mvc.perform(get("/blueprints/90003").param("limit", "300").header("HX-Request", "true")
                        .with(signedInAs(sub))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(drawer).contains("limit=300");

        // 1,000 is the most: more than that, or less than a page, is brought back into range
        var last = mvc.perform(get("/blueprints").param("limit", "5000").with(signedInAs(sub))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(rowsIn(last)).isEqualTo(1_000);
        assertThat(last).contains("Showing 1,000 of 1,150 blueprints", "id=\"blueprints-capped\"")
                .doesNotContain("blueprints-show-more");
        assertThat(rowsIn(mvc.perform(get("/blueprints").param("limit", "3").with(signedInAs(sub)))
                .andReturn().getResponse().getContentAsString())).isEqualTo(100);

        // a filter counts what it matches, not what the page shows
        var kind = mvc.perform(get("/blueprints").param("me", "10").with(signedInAs(sub))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(kind).containsPattern("of [0-9]+ blueprints").doesNotContain("1,150");
        // the CSV takes every picked row, however many pages they were picked from
        var ids = new String[1_150];
        for (var i = 0; i < ids.length; i++) {
            ids[i] = Long.toString(90_000L + i);
        }
        var csv = mvc.perform(post("/blueprints/export.csv").param("item", ids).with(csrf()).with(signedInAs(sub)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(csv.split("\n", -1).length - 1).isEqualTo(1_151);
    }

    @Test
    void theHolderTableIsOneLineUntilOpened() throws Exception {
        var sub = newSub();
        var viewer = viewerFor(sub);
        turnOn(viewer, Feature.BLUEPRINTS);
        var character = link(viewer, "Summary Character", BLUEPRINT_SCOPE);
        link(viewer, "Scopeless Character", ASSETS_SCOPE);
        esi.blueprintsOf(character.eveId(), manyBlueprints(3));

        var before = mvc.perform(get("/blueprints/status").with(signedInAs(sub))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(before).contains("<details id=\"blueprint-status-details\"").contains("2 holders", "1 needs attention")
                .doesNotContain("open");

        mvc.perform(get("/blueprints/status").param("start", "1").with(signedInAs(sub))).andExpect(status().isOk());
        await().atMost(10, TimeUnit.SECONDS).until(() -> !fetcher.anyRunning(viewer));
        var after = mvc.perform(get("/blueprints/status").with(signedInAs(sub))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(after).contains("2 holders", "1 needs attention");
        // the holders are still all listed for troubleshooting, inside the details
        assertThat(after).contains("Summary Character", "Scopeless Character", "Up to date");

        var only = newSub();
        var onlyViewer = viewerFor(only);
        turnOn(onlyViewer, Feature.BLUEPRINTS);
        var fine = link(onlyViewer, "Fine Character", BLUEPRINT_SCOPE);
        esi.blueprintsOf(fine.eveId(), manyBlueprints(1));
        startAndWait(only, onlyViewer);
        assertThat(mvc.perform(get("/blueprints/status").with(signedInAs(only))).andReturn().getResponse()
                .getContentAsString()).contains("1 holder · all up to date · read ").doesNotContain("needs attention");
    }

    @Test
    void aDirectorWhoFailedWhileAnotherWorkedIsNotAskedAtEveryVisit() throws Exception {
        var sub = newSub();
        var viewer = viewerFor(sub);
        turnOn(viewer, Feature.INVENTORY, Feature.CORPORATION_ASSETS, Feature.CORPORATION_BLUEPRINTS);
        var scopes = new String[] {ASSETS_SCOPE, CORP_ASSETS_SCOPE, CORP_DIVISIONS_SCOPE, CORP_ROLES_SCOPE,
                CORP_BLUEPRINT_SCOPE};
        var lost = link(viewer, "Director Who Lost The Role", scopes);
        var working = link(viewer, "Director Who Works", scopes);
        var corporationId = 98_500_001L + ThreadLocalRandom.current().nextInt(100_000);
        esi.inCorporation(lost.eveId(), corporationId, "Director");
        esi.inCorporation(working.eveId(), corporationId, "Director");
        esi.corporationNamed(corporationId, "Two Director Corporation");
        esi.corporationAssetsOf(corporationId, List.of(asset(89000, 27, 60003760L, "station", "OfficeFolder", null)));
        esi.corporationBlueprintsOf(corporationId, List.of(new CharacterBlueprint(89001, 2001, 89000L, "CorpSAG2", -1, 10, 20, -1)));
        esi.refuseDirector(lost.eveId());

        startAndWait(sub, viewer);
        var refusedFirst = refusals(corporationId, lost.eveId());
        assertThat(esi.corporationBlueprintsAsked()).contains(corporationId + ":" + working.eveId());
        mvc.perform(get("/blueprints").with(signedInAs(sub))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Two Director Corporation")))
                .andExpect(content().string(containsString("Test Hauler Blueprint")));

        // the next visits go straight to the Director who works: the other is not asked again
        startAndWait(sub, viewer);
        startAndWait(sub, viewer);
        assertThat(refusals(corporationId, lost.eveId())).isEqualTo(refusedFirst);

        // the roles change: the Director who worked is refused now, and the one avoided is back in the role
        esi.refuseDirector(working.eveId());
        esi.acceptDirector(lost.eveId());
        startAndWait(sub, viewer);
        assertThat(refusals(corporationId, lost.eveId())).as("avoided for a while").isEqualTo(refusedFirst);

        // the button asks everything again, so a Director who may have been put right is tried once more
        mvc.perform(post("/blueprints/refresh").with(csrf()).with(signedInAs(sub))).andExpect(status().is3xxRedirection());
        await().atMost(10, TimeUnit.SECONDS).until(() -> !fetcher.anyRunning(viewer));
        assertThat(refusals(corporationId, lost.eveId())).as("asked again after the button").isGreaterThan(refusedFirst);
    }

    /** How many corporation calls went through the character: the refused ones, for assets and for blueprints. */
    private long refusals(long corporationId, long eveCharacterId) {
        var key = corporationId + ":" + eveCharacterId;
        return esi.corporationBlueprintsAsked().stream().filter(key::equals).count()
                + esi.corporationAssetsAsked().stream().filter(key::equals).count();
    }

    private static String seenIn(String page) {
        var matcher = java.util.regex.Pattern.compile("name=\"seen\" value=\"([^\"]+)\"").matcher(page);
        assertThat(matcher.find()).as("the page says what it shows").isTrue();
        return matcher.group(1);
    }

    private String refreshAsked(String sub, String seen, String... filter) throws Exception {
        var request = get("/blueprints/status").header("HX-Request", "true").with(signedInAs(sub));
        if (seen != null) {
            request = request.param("seen", seen);
        }
        for (var i = 0; i + 1 < filter.length; i += 2) {
            request = request.param(filter[i], filter[i + 1]);
        }
        return mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getHeader("HX-Trigger");
    }

    @Test
    void theTableIsAskedForAgainOnlyWhenWhatItShowsHasChanged() throws Exception {
        var sub = newSub();
        var viewer = viewerFor(sub);
        turnOn(viewer, Feature.BLUEPRINTS);
        var character = link(viewer, "Change Character", BLUEPRINT_SCOPE);
        esi.blueprintsOf(character.eveId(), manyBlueprints(5));
        startAndWait(sub, viewer);
        var seen = seenIn(mvc.perform(get("/blueprints").with(signedInAs(sub))).andReturn().getResponse()
                .getContentAsString());

        // a read that changed nothing: the table is not sent again
        assertThat(refreshAsked(sub, seen)).isNull();
        startAndWait(sub, viewer);
        assertThat(refreshAsked(sub, seen)).isNull();
        // a page that shows something else, and a poll that does not say what the page shows
        assertThat(refreshAsked(sub, "0")).isEqualTo("blueprints-refresh");
        assertThat(refreshAsked(sub, null)).isNull();
        // the filter is part of what is shown: the same data under another filter is another table
        assertThat(refreshAsked(sub, seen, "kind", "BPC")).isEqualTo("blueprints-refresh");
        var filtered = seenIn(mvc.perform(get("/blueprints").param("kind", "BPC").with(signedInAs(sub)))
                .andReturn().getResponse().getContentAsString());
        assertThat(refreshAsked(sub, filtered, "kind", "BPC")).isNull();

        // a read that changed a blueprint: now it is
        esi.blueprintsOf(character.eveId(), manyBlueprints(6));
        startAndWait(sub, viewer);
        assertThat(refreshAsked(sub, seen)).isEqualTo("blueprints-refresh");
        var after = seenIn(mvc.perform(get("/blueprints").with(signedInAs(sub))).andReturn().getResponse()
                .getContentAsString());
        assertThat(after).isNotEqualTo(seen);
        assertThat(refreshAsked(sub, after)).isNull();

        // a poll while a read runs never asks: the read is not finished
        mvc.perform(get("/blueprints/status").param("start", "1").header("HX-Request", "true").param("seen", "0")
                .with(signedInAs(sub))).andExpect(status().isOk());
        await().atMost(10, TimeUnit.SECONDS).until(() -> !fetcher.anyRunning(viewer));
    }

    /**
     * The page says "UTC" after the time, so the time must be in UTC. A machine that runs the build in UTC would hide a
     * time formatted in the JVM's own zone, so the test puts the JVM in a zone a long way from UTC.
     */
    @Test
    void theFetchedTimeIsInUtcWhateverZoneTheJvmIsIn() throws Exception {
        var original = java.util.TimeZone.getDefault();
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Pacific/Auckland"));
        try {
            var sub = newSub();
            var viewer = viewerFor(sub);
            turnOn(viewer, Feature.BLUEPRINTS);
            var character = link(viewer, "Time Character", BLUEPRINT_SCOPE);
            esi.blueprintsOf(character.eveId(), manyBlueprints(2));
            startAndWait(sub, viewer);

            var fetchedAt = blueprintRepository.characterStates(List.of(character.id())).getFirst().fetchedAt();
            var pattern = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            var inUtc = pattern.withZone(java.time.ZoneOffset.UTC).format(fetchedAt) + " UTC";
            var inAuckland = pattern.withZone(java.time.ZoneId.of("Pacific/Auckland")).format(fetchedAt) + " UTC";
            assertThat(inUtc).isNotEqualTo(inAuckland);

            var status = mvc.perform(get("/blueprints/status").with(signedInAs(sub))).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(status).contains(inUtc).doesNotContain(inAuckland);
        } finally {
            java.util.TimeZone.setDefault(original);
        }
    }

    private static String rowOf(String page, long itemId) {
        var start = page.indexOf("data-blueprint-row=\"" + itemId + "\"");
        assertThat(start).as("the page has a row for " + itemId).isGreaterThanOrEqualTo(0);
        return page.substring(start, page.indexOf("</tr>", start));
    }

    @Test
    void theHolderColumnHasTheColourOfTheHolderAsInTheInventoryBars() throws Exception {
        var sub = newSub();
        var viewer = viewerFor(sub);
        turnOn(viewer, Feature.INVENTORY, Feature.BLUEPRINTS, Feature.CORPORATION_ASSETS, Feature.CORPORATION_BLUEPRINTS);
        var plainCorporation = 98_600_001L + ThreadLocalRandom.current().nextInt(100_000);
        var readableCorporation = plainCorporation + 1_000_000L;
        esi.corporationNamed(plainCorporation, "Plain Corporation");
        esi.corporationNamed(readableCorporation, "Readable Corporation");
        esi.corporationAssetsOf(readableCorporation, List.of(asset(92000, 27, 60003760L, "station", "OfficeFolder", null)));
        esi.corporationBlueprintsOf(readableCorporation,
                List.of(new CharacterBlueprint(92003, 2001, 92000L, "CorpSAG2", -1, 10, 20, -1)));
        // linked in this order, which fixes the colours of the characters: the first is 0 and the second is 1
        var first = link(viewer, "First Linked", ASSETS_SCOPE, BLUEPRINT_SCOPE, CORP_ROLES_SCOPE);
        esi.inCorporation(first.eveId(), plainCorporation);
        var second = link(viewer, "Second Linked", ASSETS_SCOPE, BLUEPRINT_SCOPE, CORP_ASSETS_SCOPE, CORP_DIVISIONS_SCOPE,
                CORP_ROLES_SCOPE, CORP_BLUEPRINT_SCOPE);
        esi.inCorporation(second.eveId(), readableCorporation, "Director");
        esi.blueprintsOf(first.eveId(), List.of(new CharacterBlueprint(92001, 2001, 60003760L, "Hangar", -1, 4, 8, -1)));
        esi.blueprintsOf(second.eveId(), List.of(new CharacterBlueprint(92002, 2001, 60003760L, "Hangar", -1, 4, 8, -1)));
        // the inventory page names every corporation of the account, the one nobody can read from here included
        mvc.perform(get("/inventory/status").param("start", "1").with(signedInAs(sub))).andExpect(status().isOk());
        await().atMost(10, TimeUnit.SECONDS).until(() -> !corporationFetcher.anyRunning(viewer));
        startAndWait(sub, viewer);

        var page = mvc.perform(get("/blueprints").with(signedInAs(sub))).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString();

        assertThat(rowOf(page, 92001)).contains("class=\"seg-0\"", "First Linked");
        assertThat(rowOf(page, 92002)).contains("class=\"seg-1\"", "Second Linked");
        // two characters, then the corporations by name: Plain is 2 and Readable is 3, though this page shows only
        // Readable, so counting among the corporations it shows would make it 2 here and 3 in the inventory bars
        assertThat(rowOf(page, 92003)).contains("class=\"seg-3\"", "Readable Corporation");
        assertThat(page).doesNotContain("Plain Corporation");
    }

    @Test
    void sortsByEveryColumnAndAHistoryStepIsAnsweredWithTheWholePage() throws Exception {
        var sub = newSub();
        var viewer = viewerFor(sub);
        turnOn(viewer, Feature.INVENTORY, Feature.BLUEPRINTS);
        var character = link(viewer, "Sorting Character", ASSETS_SCOPE, BLUEPRINT_SCOPE);
        esi.blueprintsOf(character.eveId(), List.of(new CharacterBlueprint(84001, 2001, 60003760L, "Hangar", -1, 4, 8, -1)));
        startAndWait(sub, viewer);

        for (var column : List.of("type", "kind", "status", "me", "te", "runs", "holder", "place")) {
            mvc.perform(get("/blueprints").param("sort", column).param("dir", "desc").with(signedInAs(sub)))
                    .andExpect(status().isOk()).andExpect(content().string(containsString("Test Hauler Blueprint")));
        }

        // htmx 4.0.0 asks for a step back in the history with HX-History-Restore-Request and without HX-Request (measured
        // in a browser, see the Back tests in BlueprintsBrowserIT), so it is a plain request for the page
        var restore = mvc.perform(get("/blueprints").header("HX-History-Restore-Request", "true").with(signedInAs(sub)))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(restore.getContentAsString()).contains("<html", "id=\"blueprint-drawer\"");
        // the same address answers the page and, to htmx, the list alone, so a cache must tell them apart
        assertThat(restore.getHeaders("Vary")).anyMatch(value -> value.contains("HX-Request"));
        var fragment = mvc.perform(get("/blueprints").header("HX-Request", "true").with(signedInAs(sub)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(fragment).doesNotContain("<html", "id=\"blueprint-drawer\"");

        var drawer = mvc.perform(get("/blueprints/84001").param("sort", "me").header("HX-Request", "true")
                        .with(signedInAs(sub)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(drawer).contains("data-return-url=\"/blueprints?").contains("sort=me");
    }

    @Test
    void anItemInTheCharacterAndCorporationListsIsExportedOnce() throws Exception {
        var sub = newSub();
        var viewer = viewerFor(sub);
        turnOn(viewer, Feature.INVENTORY, Feature.BLUEPRINTS, Feature.CORPORATION_ASSETS, Feature.CORPORATION_BLUEPRINTS);
        var scopes = new String[] {ASSETS_SCOPE, BLUEPRINT_SCOPE, CORP_ASSETS_SCOPE, CORP_DIVISIONS_SCOPE,
                CORP_ROLES_SCOPE, CORP_BLUEPRINT_SCOPE};
        var director = link(viewer, "Moving Director", scopes);
        var corporationId = 98_300_001L + ThreadLocalRandom.current().nextInt(100_000);
        esi.inCorporation(director.eveId(), corporationId, "Director");
        esi.corporationNamed(corporationId, "Moving Corporation");
        esi.hangars(corporationId, java.util.Map.of(2, "Blueprint Hangar"));
        esi.corporationAssetsOf(corporationId, List.of(asset(85000, 27, 60003760L, "station", "OfficeFolder", null)));
        // the same item, still in the character's cached list and already in the corporation's
        esi.blueprintsOf(director.eveId(), List.of(new CharacterBlueprint(85001, 2001, 60003760L, "Hangar", -1, 4, 8, -1)));
        esi.corporationBlueprintsOf(corporationId,
                List.of(new CharacterBlueprint(85001, 2001, 85000L, "CorpSAG2", -1, 4, 8, -1)));
        startAndWait(sub, viewer);

        var csv = mvc.perform(post("/blueprints/export.csv").param("item", "85001").param("item", "85001")
                        .with(csrf()).with(signedInAs(sub)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(csv.split("Test Hauler Blueprint", -1)).hasSize(2);
        mvc.perform(post("/blueprints/export.csv").param("item", "85001").with(signedInAs(sub)))
                .andExpect(status().isForbidden());
    }

    @Test
    void corporationBlueprintsAreReadThroughAQualifiedDirector() throws Exception {
        var sub = newSub();
        var viewer = viewerFor(sub);
        turnOn(viewer, Feature.INVENTORY, Feature.CORPORATION_ASSETS, Feature.CORPORATION_BLUEPRINTS);
        var scopes = new String[] {ASSETS_SCOPE, CORP_ASSETS_SCOPE, CORP_DIVISIONS_SCOPE, CORP_ROLES_SCOPE,
                CORP_BLUEPRINT_SCOPE};
        var director = link(viewer, "Corporation Director", scopes);
        var corporationId = 98_200_001L + ThreadLocalRandom.current().nextInt(100_000);
        esi.inCorporation(director.eveId(), corporationId, "Director");
        esi.corporationNamed(corporationId, "Test Corporation");
        var officeId = 82999L;
        esi.hangars(corporationId, java.util.Map.of(2, "Blueprint Hangar"));
        esi.corporationAssetsOf(corporationId, List.of(
                asset(officeId, 27, 60003760L, "station", "OfficeFolder", null),
                asset(82001, 2001, officeId, "item", "CorpSAG2", false)));
        esi.corporationBlueprintsOf(corporationId,
                List.of(new CharacterBlueprint(82001, 2001, officeId, "CorpSAG2", -1, 10, 20, -1)));

        startAndWait(sub, viewer);

        assertThat(esi.corporationBlueprintsAsked()).containsExactly(corporationId + ":" + director.eveId());
        mvc.perform(get("/blueprints").with(signedInAs(sub))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Test Corporation")))
                .andExpect(content().string(containsString("Test Hauler Blueprint")))
                .andExpect(content().string(containsString("Blueprint Hangar")));
    }

    @Test
    void disabledFeatureDoesNotReadBlueprintsAndForeignItemDetailsAreNotFound() throws Exception {
        var sub = newSub();
        var viewer = viewerFor(sub);
        link(viewer, "Feature Off", BLUEPRINT_SCOPE);
        mvc.perform(get("/blueprints").with(signedInAs(sub))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Turn on blueprint reads")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("blueprint-actions"))));
        mvc.perform(get("/blueprints/status").param("start", "1").with(signedInAs(sub))).andExpect(status().isOk());
        await().atMost(5, TimeUnit.SECONDS).until(() -> esi.blueprintsAsked().isEmpty());

        var otherSub = newSub();
        var other = viewerFor(otherSub);
        turnOn(other, Feature.INVENTORY, Feature.BLUEPRINTS);
        var owned = link(other, "Other Owner", ASSETS_SCOPE, BLUEPRINT_SCOPE);
        esi.blueprintsOf(owned.eveId(), List.of(new CharacterBlueprint(83001, 2001, 60003760L, "Hangar", -1,
                10, 20, -1)));
        startAndWait(otherSub, other);

        var strangerSub = newSub();
        viewerFor(strangerSub);
        mvc.perform(get("/blueprints/83001").with(signedInAs(strangerSub))).andExpect(status().isNotFound());
    }

    @Test
    void refreshRequiresCsrfAndPageStartsReadsOnlyAfterStatusLoads() throws Exception {
        var sub = newSub();
        var viewer = viewerFor(sub);
        turnOn(viewer, Feature.INVENTORY, Feature.BLUEPRINTS);
        var character = link(viewer, "Refresh Character", ASSETS_SCOPE, BLUEPRINT_SCOPE);

        var page = mvc.perform(get("/blueprints").with(signedInAs(sub))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(page).contains("hx-get=\"/blueprints/status?start=1\"").contains("hx-trigger=\"load\"");
        assertThat(esi.blueprintsAsked()).isEmpty();

        mvc.perform(post("/blueprints/refresh").with(signedInAs(sub)).with(csrf()))
                .andExpect(redirectedUrl("/blueprints"));
        await().atMost(10, TimeUnit.SECONDS).until(() -> esi.blueprintsAsked().contains(character.eveId()));
        mvc.perform(post("/blueprints/refresh").with(signedInAs(sub))).andExpect(status().isForbidden());
    }
}