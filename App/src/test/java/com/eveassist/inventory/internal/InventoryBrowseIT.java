package com.eveassist.inventory.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eveassist.IntegrationTest;
import com.eveassist.esi.CharacterAsset;
import com.eveassist.identity.Viewer;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** The inventory page as a browser asks for it: the whole page, the list alone, the lists of values and the export. */
@AutoConfigureMockMvc
class InventoryBrowseIT extends IntegrationTest {

    private static final long JITA = 60003760L;
    private static final long AMARR = 60008494L;
    private static final long UNKNOWN_STRUCTURE = 1_000_000_000_002L;
    private static final int TRITANIUM = 34;
    private static final int PILOTS_MINERAL = 35;
    private static final int HAULER = 1001;
    private static final int COMPONENT = 1002;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    AssetRepository assets;

    @Autowired
    BrowseService browse;

    @Autowired
    CorporationRepository corporations;

    private RequestPostProcessor signedInAs(String sub) {
        return oidcLogin().idToken(token -> token.subject(sub)).authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private UUID link(Viewer viewer, String name) {
        var id = UUID.randomUUID();
        var eve = 2_000_000_000L + ThreadLocalRandom.current().nextInt(1_000_000_000);
        jdbc.sql("""
                INSERT INTO character (public_id, owner_account_id, eve_character_id, name, owner_hash, scopes, state)
                VALUES (:id, :owner, :eve, :name, 'hash', :scopes, 'ACTIVE')
                """)
                .param("id", id).param("owner", viewer.accountId().value()).param("eve", eve).param("name", name)
                .param("scopes", new String[] {"esi-assets.read_assets.v1"}).update();
        // opening the page asks ESI about every character, and ESI says its copy has not changed: the stored rows stay
        esi.assetsUnchanged(eve);
        return id;
    }

    private void store(UUID character, CharacterAsset... items) {
        assets.replace(character, List.of(items), Instant.now());
    }

    private static CharacterAsset asset(long itemId, int typeId, long quantity, long location, String flag) {
        return new CharacterAsset(itemId, typeId, quantity, location, "station", flag, false, null);
    }

    private String whole(String sub, String query) throws Exception {
        return mvc.perform(get("/inventory" + query).with(signedInAs(sub)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("??"))))
                .andReturn().getResponse().getContentAsString();
    }

    private String partial(String sub, String query) throws Exception {
        return mvc.perform(get("/inventory" + query).with(signedInAs(sub)).header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("??"))))
                .andReturn().getResponse().getContentAsString();
    }

    private static String text(String html) {
        return org.springframework.web.util.HtmlUtils.htmlUnescape(html.replaceAll("<[^>]+>", " ")).replaceAll("\\s+", " ");
    }

    /** An account with two characters holding a few things in two stations. */
    private record Setup(String sub, Viewer viewer, UUID one, UUID two) {
    }

    private Setup setup() {
        var sub = newSub();
        var viewer = viewerFor(sub);
        var one = link(viewer, "Alt One");
        var two = link(viewer, "Alt Two");
        store(one, asset(1, TRITANIUM, 1_500, JITA, "Hangar"), asset(2, HAULER, 1, AMARR, "Hangar"));
        store(two, asset(1, TRITANIUM, 500, JITA, "Hangar"), asset(2, PILOTS_MINERAL, 20, JITA, "Hangar"),
                asset(3, COMPONENT, 5, AMARR, "Hangar"));
        return new Setup(sub, viewer, one, two);
    }

    @Test
    void theWholePageListsTheTotalsWithABarAndTheCharactersShown() throws Exception {
        var s = setup();

        var page = whole(s.sub(), "");

        assertThat(page).contains("<html").contains("id=\"browse-form\"").contains("id=\"items\"");
        var visible = text(page);
        assertThat(visible).contains("Tritanium", "2,000", "Pilot's Mineral", "Test Hauler", "Test Component");
        assertThat(visible).contains("4 item types, 2,026 units");
        assertThat(visible).contains("2 characters, most: Alt One");
        // a bar of pieces, one per character, in each character's own colour, drawn as an image and not a style
        assertThat(page).contains("<svg").contains("class=\"seg-0\"").contains("class=\"seg-1\"");
        assertThat(page).doesNotContain("style=");
        assertThat(page).contains("Alt One: 1,500").contains("Alt Two: 500");
    }

    @Test
    void theListIsSortedByUnitsAndEachCharacterKeepsItsColour() throws Exception {
        var s = setup();

        var page = whole(s.sub(), "");

        assertThat(page.indexOf("Tritanium")).isLessThan(page.indexOf("Pilot&#39;s Mineral"));
        assertThat(page.indexOf("Pilot&#39;s Mineral")).isLessThan(page.indexOf("Test Component"));
        // Alt One and Alt Two have the same colours in every bar: the one with the lower EVE id has colour 0
        var firstEve = jdbc.sql("SELECT eve_character_id FROM character WHERE public_id = :id").param("id", s.one())
                .query(Long.class).single();
        var secondEve = jdbc.sql("SELECT eve_character_id FROM character WHERE public_id = :id").param("id", s.two())
                .query(Long.class).single();
        var lowerIsOne = firstEve < secondEve;
        var tritaniumBar = page.substring(page.indexOf("<svg"), page.indexOf("</svg>"));
        assertThat(tritaniumBar.indexOf(lowerIsOne ? "Alt One" : "Alt Two")).isLessThan(tritaniumBar.length());
        assertThat(tritaniumBar).contains("seg-0").contains("seg-1");
    }

    @Test
    void anHtmxRequestGetsTheListAloneWithTheNewHiddenFieldsAndTheVaryHeader() throws Exception {
        var s = setup();

        var result = mvc.perform(get("/inventory").param("ui", "1").param("chars", "1").param("category", "4")
                        .with(signedInAs(s.sub())).header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("Vary", containsString("HX-Request")))
                .andReturn().getResponse().getContentAsString();

        assertThat(result).doesNotContain("<html").doesNotContain("id=\"browse-form\"");
        assertThat(result).contains("id=\"items\"").contains("id=\"filter-state\"").contains("hx-swap-oob=\"true\"");
        assertThat(result).contains("name=\"category\" value=\"4\"");
        // no list is open, so the open list is closed by the same answer
        assertThat(result).contains("id=\"filter-pop\"").contains("hx-swap-oob=\"innerHTML\"");
        assertThat(text(result)).contains("2 item types").contains("Tritanium").doesNotContain("Test Hauler");
    }

    @Test
    void anOpenColumnIsLeftOutOfTheHiddenFieldsAndItsListStaysOpen() throws Exception {
        var s = setup();

        var result = partial(s.sub(), "?ui=1&chars=1&category=4&open=category&group=3");

        assertThat(result).doesNotContain("name=\"category\" value=\"4\"");
        assertThat(result).contains("name=\"group\" value=\"3\"");
        assertThat(result).doesNotContain("hx-swap-oob=\"innerHTML\"");
    }

    @Test
    void doneClosesTheListEvenThoughTheFormStillSendsItsOpenField() throws Exception {
        var s = setup();

        var result = mvc.perform(get("/inventory").param("ui", "1").param("open", "category").param("open", "")
                        .param("category", "4").with(signedInAs(s.sub())).header("HX-Request", "true"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(result).contains("name=\"category\" value=\"4\"").contains("hx-swap-oob=\"innerHTML\"");
    }

    @Test
    void filtersFromDifferentColumnsAllApplyAndTheChipsSayWhichAreOn() throws Exception {
        var s = setup();

        var result = partial(s.sub(), "?ui=1&chars=1&category=4&character=" + s.two() + "&place=" + JITA
                + "&how=HANGAR&min=10");
        var visible = text(result);

        // Alt Two's Material in Jita: 500 Tritanium and 20 Pilot's Mineral. Alt One's 1,500 are not counted.
        assertThat(visible).contains("2 item types, 520 units").contains("Tritanium").contains("Pilot's Mineral");
        assertThat(visible).contains("Category: Material", "Character: Alt Two", "Where: Jita IV - Moon 4",
                "How held: in a hangar", "Total at least: 10");
        assertThat(visible).contains("Clear all");
        assertThat(text(result)).doesNotContain("Test Hauler");
    }

    @Test
    void aChipTakesItsFilterAwayAndLeavesTheRest() throws Exception {
        var s = setup();

        var result = partial(s.sub(), "?ui=1&chars=1&category=4&place=" + JITA);

        assertThat(result).contains("/inventory?ui=1&amp;place=" + JITA + "&amp;chars=1");
        assertThat(result).contains("/inventory?ui=1&amp;category=4&amp;chars=1");
    }

    @Test
    void valuesInOneColumnAreChipTextWithOrAndAfterTwoACount() throws Exception {
        var s = setup();

        assertThat(text(partial(s.sub(), "?ui=1&chars=1&category=4&category=6"))).contains("Category: Material or Ship");
        assertThat(text(partial(s.sub(), "?ui=1&chars=1&category=4&category=6&category=17")))
                .contains("Category: 3 selected");
    }

    @Test
    void searchNarrowsTheListAndAHostileSearchIsHarmlessAndEscaped() throws Exception {
        var s = setup();

        assertThat(text(partial(s.sub(), "?ui=1&chars=1&q=trit"))).contains("Tritanium").doesNotContain("Test Hauler");
        var hostile = partial(s.sub(), "?ui=1&chars=1&q=%3Cscript%3Ealert(1)%3C%2Fscript%3E");
        assertThat(hostile).doesNotContain("<script>alert(1)");
        assertThat(text(hostile)).contains("No items match these filters.");
        assertThat(partial(s.sub(), "?ui=1&chars=1&q=%25")).contains("id=\"no-match\"");
    }

    @Test
    void theAddressIsTheWholeFilterSoAReloadShowsTheSameList() throws Exception {
        var s = setup();
        var query = "?ui=1&category=4&character=" + s.two() + "&sort=name&chars=1";

        var page = whole(s.sub(), query);

        assertThat(text(page)).contains("Category: Material", "Character: Alt Two").contains("2 item types");
        assertThat(page).contains("name=\"category\" value=\"4\"").contains("name=\"character\" value=\"" + s.two() + "\"");
        assertThat(page).contains("<option value=\"name\" selected");
        assertThat(page.indexOf("Pilot&#39;s Mineral")).isLessThan(page.indexOf("Tritanium"));
    }

    @Test
    void charactersCanBeLeftOutAndTheBarGoesWithThem() throws Exception {
        var s = setup();

        var hidden = partial(s.sub(), "?ui=1");

        assertThat(hidden).doesNotContain("<svg").doesNotContain("class=\"seg-");
        assertThat(text(hidden)).contains("Tritanium").doesNotContain("Held by");
        assertThat(partial(s.sub(), "?ui=1&chars=1")).contains("<svg");
        // with nothing said, they are shown
        assertThat(partial(s.sub(), "")).contains("<svg");
        assertThat(whole(s.sub(), "?ui=1")).doesNotContain("class=\"seg-");
    }

    @Test
    void aColumnsListShowsItsValuesWithCountsAndWhatIsTicked() throws Exception {
        var s = setup();

        var list = mvc.perform(get("/inventory/filter/category").param("ui", "1").param("category", "4")
                        .with(signedInAs(s.sub())).header("HX-Request", "true"))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString("??"))))
                .andReturn().getResponse().getContentAsString();

        assertThat(list).contains("name=\"open\" value=\"category\"").contains("id=\"filter-done\"");
        assertThat(list).containsPattern("name=\"category\" value=\"4\"[^>]*checked");
        assertThat(list).doesNotContainPattern("name=\"category\" value=\"6\"[^>]*checked");
        var visible = text(list);
        assertThat(visible).contains("Material", "Ship", "Commodity");
        // the list leaves out its own filter, so the other categories are still offered
        assertThat(list).contains("hx-swap-oob=\"true\"").doesNotContain("name=\"category\" value=\"4\" ></div>");
    }

    @Test
    void aListHonoursTheOtherFiltersAndItsOwnSearch() throws Exception {
        var s = setup();

        var characters = mvc.perform(get("/inventory/filter/place").param("ui", "1").param("category", "6")
                        .with(signedInAs(s.sub())).header("HX-Request", "true"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        // only Amarr has a ship
        assertThat(text(characters)).contains("Amarr VIII (Oris)").doesNotContain("Jita IV");

        var searched = mvc.perform(get("/inventory/filter/place").param("fq", "jita").with(signedInAs(s.sub()))
                        .header("HX-Request", "true"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(text(searched)).contains("Jita IV").doesNotContain("Amarr VIII");
    }

    @Test
    void theHowAndMinimumListsAndTheCharacterListAreWorded() throws Exception {
        var s = setup();

        var how = mvc.perform(get("/inventory/filter/how").param("ui", "1").param("how", "FITTED").with(signedInAs(s.sub())))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString("??"))))
                .andReturn().getResponse().getContentAsString();
        assertThat(text(how)).contains("in a hangar").contains("Not fitted or in ships");
        assertThat(how).contains("/inventory?ui=1&amp;how=ASSET_SAFETY&amp;how=DELIVERIES&amp;how=HANGAR&amp;how=IN_CONTAINER");

        var minimum = mvc.perform(get("/inventory/filter/min").param("ui", "1").param("min", "250")
                        .with(signedInAs(s.sub())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(minimum).contains("name=\"min\"").contains("value=\"250\"").contains("name=\"open\" value=\"min\"");

        var characters = mvc.perform(get("/inventory/filter/character").with(signedInAs(s.sub())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(text(characters)).contains("Alt One", "Alt Two");
        assertThat(characters).contains("value=\"" + s.one() + "\"");
    }

    @Test
    void anUnknownColumnIsNotFound() throws Exception {
        var s = setup();

        mvc.perform(get("/inventory/filter/password").with(signedInAs(s.sub()))).andExpect(status().isNotFound());
        mvc.perform(get("/inventory/filter/..%2Fx").with(signedInAs(s.sub()))).andExpect(status().is4xxClientError());
    }

    @Test
    void anOpenedRowShowsWhereTheItemIsWithinTheFilter() throws Exception {
        var s = setup();
        store(s.one(), asset(1, TRITANIUM, 1_500, JITA, "Hangar"),
                new CharacterAsset(2, TRITANIUM, 40, UNKNOWN_STRUCTURE, "item", "Hangar", false, null),
                new CharacterAsset(3, TRITANIUM, 7, 1, "item", "Cargo", false, null));

        var where = mvc.perform(get("/inventory/item/" + TRITANIUM).param("ui", "1").param("chars", "1")
                        .with(signedInAs(s.sub())).header("HX-Request", "true"))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString("??"))))
                .andReturn().getResponse().getContentAsString();

        var visible = text(where);
        assertThat(visible).contains("Jita IV - Moon 4 - Caldari Navy Assembly Plant", "The Forge", "in a hangar",
                "Alt One 1,500", "Alt Two 500", "2,000");
        assertThat(visible).contains("Unknown structure " + UNKNOWN_STRUCTURE, "name unknown");
        // item 3 sits in item 1, a stack in Jita, so its place is Jita, held "in a ship's hold"
        assertThat(visible).contains("in a ship's hold");

        var narrowed = mvc.perform(get("/inventory/item/" + TRITANIUM).param("ui", "1").param("character", s.two().toString())
                        .with(signedInAs(s.sub())).header("HX-Request", "true"))
                .andReturn().getResponse().getContentAsString();
        assertThat(text(narrowed)).contains("500").doesNotContain("Alt One").doesNotContain("1,500");
    }

    @Test
    void nothingOfAnotherAccountIsListedOfferedOrExported() throws Exception {
        var s = setup();
        var other = viewerFor(newSub());
        var theirs = link(other, "Not Mine");
        store(theirs, asset(1, TRITANIUM, 999_999, JITA, "Hangar"), asset(2, HAULER, 77, AMARR, "Hangar"));

        var page = text(whole(s.sub(), ""));
        assertThat(page).doesNotContain("Not Mine").doesNotContain("999,999").contains("2,000");

        var characters = text(mvc.perform(get("/inventory/filter/character").with(signedInAs(s.sub())))
                .andReturn().getResponse().getContentAsString());
        assertThat(characters).doesNotContain("Not Mine");
        assertThat(mvc.perform(get("/inventory/export.csv").with(signedInAs(s.sub()))).andReturn().getResponse()
                .getContentAsString()).doesNotContain("Not Mine").doesNotContain("999999");
        // asking for the other account's character by id changes nothing
        assertThat(text(partial(s.sub(), "?ui=1&character=" + theirs))).contains("No items match");
        assertThat(text(mvc.perform(get("/inventory/item/" + HAULER).param("character", theirs.toString())
                        .with(signedInAs(s.sub())).header("HX-Request", "true")).andReturn().getResponse()
                .getContentAsString())).doesNotContain("77");
    }

    @Test
    void showsAnAccountWithNoCharactersAMessageAndNoFilterForm() throws Exception {
        var sub = newSub();
        viewerFor(sub);

        var page = whole(sub, "");

        assertThat(page).contains("id=\"no-assets\"").doesNotContain("id=\"browse-form\"");
    }

    @Test
    void anAccountWhoseCharactersHaveNoAssetsYetIsToldToWait() throws Exception {
        var sub = newSub();
        link(viewerFor(sub), "Alt One");

        var page = whole(sub, "");

        assertThat(page).contains("id=\"nothing-yet\"").contains("id=\"browse-form\"");
        assertThat(page).doesNotContain("id=\"no-match\"");
    }

    @Test
    void aLongListShowsAPageAndOffersMore() throws Exception {
        var sub = newSub();
        var viewer = viewerFor(sub);
        var one = link(viewer, "Alt One");
        store(one, asset(1, TRITANIUM, 5, JITA, "Hangar"), asset(2, PILOTS_MINERAL, 4, JITA, "Hangar"),
                asset(3, HAULER, 3, JITA, "Hangar"), asset(4, COMPONENT, 2, JITA, "Hangar"));

        var page = partial(sub, "?ui=1&chars=1&limit=2");

        assertThat(text(page)).contains("4 item types").contains("Tritanium").contains("Pilot's Mineral")
                .doesNotContain("Test Hauler");
        assertThat(page).contains("id=\"show-more\"").contains("limit=102");
        assertThat(partial(sub, "?ui=1&chars=1")).doesNotContain("id=\"show-more\"");
    }

    @Test
    void blueprintCopiesAndOriginalsAreTaggedAndSaySoAboutMeAndTe() throws Exception {
        var sub = newSub();
        var viewer = viewerFor(sub);
        var one = link(viewer, "Alt One");
        store(one, new CharacterAsset(1, 2001, 1, JITA, "station", "Hangar", true, true),
                new CharacterAsset(2, 2002, 1, JITA, "station", "Hangar", true, null));

        var page = text(whole(sub, ""));

        assertThat(page).contains("Test Hauler Blueprint BPC ME/TE not read yet");
        assertThat(page).contains("Test Component Blueprint BPO ME/TE not read yet");
    }

    @Test
    void aCharacterNameWithMarkupIsEscapedInTheBarsTheChipsAndTheList() throws Exception {
        var sub = newSub();
        var viewer = viewerFor(sub);
        var odd = link(viewer, "<img src=x onerror=alert(1)>");
        store(odd, asset(1, TRITANIUM, 5, JITA, "Hangar"));

        var page = whole(sub, "?ui=1&chars=1&character=" + odd);
        var list = mvc.perform(get("/inventory/filter/character").with(signedInAs(sub))).andReturn().getResponse()
                .getContentAsString();
        var where = mvc.perform(get("/inventory/item/" + TRITANIUM).with(signedInAs(sub))).andReturn().getResponse()
                .getContentAsString();

        for (var html : List.of(page, list, where)) {
            assertThat(html).doesNotContain("<img src=x").contains("&lt;img src=x onerror=alert(1)&gt;");
        }
    }

    @Test
    void theCsvIsAttachedHasAHeaderAndOneLinePerItemCharacterPlaceAndHolding() throws Exception {
        var s = setup();

        var result = mvc.perform(get("/inventory/export.csv").param("ui", "1").param("category", "4")
                        .with(signedInAs(s.sub())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"inventory.csv\""))
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andReturn().getResponse();
        var csv = result.getContentAsString();

        var lines = csv.split("\r\n");
        assertThat(lines[0]).isEqualTo("\"Item\",\"Category\",\"Group\",\"Character\",\"Where\",\"Region\",\"How held\","
                + "\"Quantity\",\"Blueprint\",\"Hangar\"");
        assertThat(csv).contains("\"Tritanium\",\"Material\",\"Mineral\",\"Alt One\",\"Jita IV - Moon 4 - Caldari Navy "
                + "Assembly Plant\",\"The Forge\",\"in a hangar\",\"1500\"");
        assertThat(csv).contains("\"Pilot's Mineral\"").doesNotContain("Test Hauler");
        assertThat(lines).hasSize(4);
    }

    @Test
    void theStatusAnswersWithAnEventOnceTheFetchIsDoneAndNotBefore() throws Exception {
        var s = setup();

        mvc.perform(get("/inventory/status").with(signedInAs(s.sub())).header("HX-Request", "true"))
                .andExpect(status().isOk()).andExpect(header().string("HX-Trigger", "browse-refresh"))
                .andExpect(content().string(not(containsString("hx-get"))));
        mvc.perform(get("/inventory/status").with(signedInAs(s.sub()))).andExpect(header().doesNotExist("HX-Trigger"));
    }

    @Test
    void theStatusIsOneLineThatOpensIntoATable() throws Exception {
        var s = setup();

        var page = whole(s.sub(), "");

        assertThat(page).contains("<details").contains("id=\"fetch-status\"");
        assertThat(text(page)).contains("2 characters:");
        assertThat(page).contains("id=\"asset-row-" + s.one() + "\"");
    }

    @Test
    void copyAsTextGivesTheWholeListAsNameTabTotalAndFollowsTheFilter() throws Exception {
        var s = setup();

        var all = mvc.perform(get("/inventory/export.txt").param("ui", "1").with(signedInAs(s.sub())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/plain")))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andReturn().getResponse().getContentAsString();

        assertThat(all.split("\n")).containsExactly("Tritanium\t2000", "Pilot's Mineral\t20", "Test Component\t5",
                "Test Hauler\t1");
        var narrowed = mvc.perform(get("/inventory/export.txt").param("ui", "1").param("category", "4")
                        .param("min", "100").with(signedInAs(s.sub())))
                .andReturn().getResponse().getContentAsString();
        assertThat(narrowed).isEqualTo("Tritanium\t2000\n");
        // the button asks for exactly the filter that is on screen
        assertThat(partial(s.sub(), "?ui=1&chars=1&category=4")).contains("/inventory/export.txt?ui=1&amp;category=4&amp;chars=1");
    }

    @Test
    void theCsvAndTheCopyFollowTheMinimumJustLikeTheList() throws Exception {
        var s = setup();

        var csv = mvc.perform(get("/inventory/export.csv").param("ui", "1").param("min", "100")
                        .with(signedInAs(s.sub())))
                .andReturn().getResponse().getContentAsString();

        assertThat(csv).contains("\"Tritanium\"").doesNotContain("Pilot's Mineral").doesNotContain("Test Hauler");
        assertThat(csv.split("\r\n")).hasSize(3);
    }

    @Test
    void aCharacterThatIsNoLongerLinkedIsNamedAsSuchInItsChip() throws Exception {
        var s = setup();

        var result = partial(s.sub(), "?ui=1&chars=1&character=" + UUID.randomUUID());

        assertThat(text(result)).contains("Character: a character that is no longer linked");
        assertThat(text(result)).doesNotContainPattern("[0-9a-f]{8}-[0-9a-f]{4}-");
    }

    @Test
    void aCharactersColourDoesNotChangeWhenACharacterWithALowerEveIdIsLinkedLater() {
        var sub = newSub();
        var viewer = viewerFor(sub);
        var first = link(viewer, "First Linked");
        store(first, asset(1, TRITANIUM, 100, JITA, "Hangar"));
        var before = colourOf(viewer, "First Linked");

        // a character with a lower EVE id, linked later: it must not take the first one's place
        var later = link(viewer, "Linked Later");
        jdbc.sql("UPDATE character SET eve_character_id = 1000 WHERE public_id = :id").param("id", later).update();
        jdbc.sql("UPDATE character SET linked_at = now() + interval '1 minute' WHERE public_id = :id")
                .param("id", later).update();
        store(later, asset(1, TRITANIUM, 50, JITA, "Hangar"));

        assertThat(colourOf(viewer, "First Linked")).isEqualTo(before);
        assertThat(colourOf(viewer, "Linked Later")).isNotEqualTo(before);
    }

    private int colourOf(Viewer viewer, String character) {
        return browse.browse(viewer, BrowseFilter.none()).items().stream().flatMap(item -> item.segments().stream())
                .filter(segment -> segment.character().equals(character)).findFirst().orElseThrow().color();
    }

    @Test
    void theInventoryAddressesNeedASignedInUser() throws Exception {
        mvc.perform(get("/inventory/filter/category")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/inventory/item/34")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/inventory/export.csv")).andExpect(status().is3xxRedirection());
    }

    // ---- the corporations of the account's own characters

    private static final long OFFICE = 1_050_000_000_001L;

    /**
     * Turns the Corporation assets feature on and gives the account a corporation with a hangar named "Production",
     * holding 900 Tritanium in hangar division 2 and a Test Hauler in division 1. The member is a known Director that
     * granted no corporation permissions, so the page's own background run can neither learn nor read anything.
     */
    private UUID corporation(Viewer viewer, UUID member, String name) {
        for (var feature : List.of("INVENTORY", "CORPORATION_ASSETS")) {
            jdbc.sql("INSERT INTO account_feature (owner_account_id, feature) VALUES (:owner, :feature) "
                            + "ON CONFLICT DO NOTHING")
                    .param("owner", viewer.accountId().value()).param("feature", feature).update();
        }
        var eveCorporation = 98_000_000L + ThreadLocalRandom.current().nextInt(1_000_000);
        var id = corporations.saveCorporation(viewer.accountId().value(), eveCorporation, name, Instant.now());
        corporations.saveMembership(member, eveCorporation, true, Instant.now(), Instant.now());
        corporations.replace(id, List.of(
                new CharacterAsset(OFFICE, 27, 1, JITA, "station", "OfficeFolder", true, null),
                new CharacterAsset(1, TRITANIUM, 900, OFFICE, "item", "CorpSAG2", false, null),
                new CharacterAsset(2, HAULER, 1, OFFICE, "item", "CorpSAG1", true, null)), Instant.now());
        corporations.replaceDivisions(id, java.util.Map.of(2, "Production"), Instant.now());
        return id;
    }

    @Test
    void aCorporationIsAnotherHolderWithItsOwnColourAndItsItemsAreInTheTotals() throws Exception {
        var s = setup();
        corporation(s.viewer(), s.one(), "Test Industries");

        var page = whole(s.sub(), "");

        var visible = text(page);
        // Tritanium: 1,500 + 500 held by characters and 900 by the corporation
        assertThat(visible).contains("Tritanium", "2,900");
        assertThat(visible).contains("3 holders, most: Alt One");
        // two characters have colours 0 and 1, and the corporation comes after them
        assertThat(page).contains("Test Industries: 900").contains("class=\"seg-2\"");
        assertThat(page).doesNotContain("style=");
        // the Add filter menu says the column also lists corporations
        assertThat(visible).contains("Character or corporation");
    }

    @Test
    void theCorporationsOfficeIsNotAnItemAndItsContentsAreAtTheStationInTheNamedHangar() throws Exception {
        var s = setup();
        corporation(s.viewer(), s.one(), "Test Industries");

        var where = text(mvc.perform(get("/inventory/item/" + TRITANIUM).param("ui", "1").param("chars", "1")
                        .with(signedInAs(s.sub())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(where).contains("Test Industries 900").contains("Production").contains("in a hangar");
        assertThat(where).contains("Alt One 1,500");
        var hauler = text(mvc.perform(get("/inventory/item/" + HAULER).param("ui", "1").param("chars", "1")
                        .with(signedInAs(s.sub())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        // division 1 has no name stored, so it is called by its number
        assertThat(hauler).contains("Hangar 1").contains("Test Industries 1");
        assertThat(text(whole(s.sub(), ""))).doesNotContain("Office");
    }

    @Test
    void theCharacterFilterCanKeepOnlyTheCorporationOrLeaveItOut() throws Exception {
        var s = setup();
        var corporation = corporation(s.viewer(), s.one(), "Test Industries");

        var only = text(whole(s.sub(), "?ui=1&chars=1&character=" + corporation));
        var without = text(whole(s.sub(), "?ui=1&chars=1&character=" + s.one() + "&character=" + s.two()));

        assertThat(only).contains("2 item types, 901 units").contains("Character or corporation: Test Industries");
        assertThat(without).contains("4 item types, 2,026 units");
        var list = text(mvc.perform(get("/inventory/filter/character").with(signedInAs(s.sub())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(list).contains("Alt One", "Alt Two", "Test Industries");
    }

    @Test
    void withTheFeatureOffTheCorporationIsNeitherShownNorCounted() throws Exception {
        var s = setup();
        corporation(s.viewer(), s.one(), "Test Industries");
        jdbc.sql("DELETE FROM account_feature WHERE owner_account_id = :owner AND feature = 'CORPORATION_ASSETS'")
                .param("owner", s.viewer().accountId().value()).update();

        var page = whole(s.sub(), "");

        assertThat(text(page)).doesNotContain("Test Industries").contains("4 item types, 2,026 units")
                .contains("2 characters, most: Alt One");
        assertThat(page).doesNotContain("id=\"corporation-status\"");
    }

    @Test
    void anotherAccountNeverSeesTheCorporationOrItsItems() throws Exception {
        var s = setup();
        corporation(s.viewer(), s.one(), "Test Industries");
        var stranger = newSub();
        var strangerViewer = viewerFor(stranger);
        var theirs = link(strangerViewer, "Stranger");
        store(theirs, asset(1, TRITANIUM, 7, JITA, "Hangar"));
        jdbc.sql("INSERT INTO account_feature (owner_account_id, feature) VALUES (:owner, 'CORPORATION_ASSETS')")
                .param("owner", strangerViewer.accountId().value()).update();

        var page = text(whole(stranger, ""));

        assertThat(page).contains("1 item type, 7 units").doesNotContain("Test Industries");
        var where = text(mvc.perform(get("/inventory/item/" + TRITANIUM).with(signedInAs(stranger)))
                .andReturn().getResponse().getContentAsString());
        assertThat(where).doesNotContain("Production").doesNotContain("900");
    }

    @Test
    void theStatusListsTheCorporationAndWhyOneCannotBeRead() throws Exception {
        var s = setup();
        corporation(s.viewer(), s.one(), "Test Industries");

        var status = text(mvc.perform(get("/inventory/status").with(signedInAs(s.sub()))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        // the member is a Director that has not granted the corporation permissions
        assertThat(status).contains("Test Industries").contains("Director needs authorization");
    }

    @Test
    void theCsvHasAHangarColumnAndNamesTheCorporationAsTheHolder() throws Exception {
        var s = setup();
        corporation(s.viewer(), s.one(), "Test Industries");

        var csv = mvc.perform(get("/inventory/export.csv").param("ui", "1").param("category", "4")
                        .with(signedInAs(s.sub())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(csv).contains("\"Test Industries\"").contains("\"900\",\"\",\"Production\"");
        // a character's line has an empty hangar
        assertThat(csv).contains("\"1500\",\"\",\"\"");
    }
}
