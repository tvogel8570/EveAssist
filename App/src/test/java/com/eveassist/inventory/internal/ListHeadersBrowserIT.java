package com.eveassist.inventory.internal;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.eveassist.IntegrationTest;
import com.eveassist.characters.Feature;
import com.eveassist.esi.CharacterAsset;
import com.eveassist.esi.CharacterBlueprint;
import com.eveassist.identity.internal.TestSignIn;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The inventory and blueprint lists: deep in a list, the filter, the count with its actions and the column headings stay in view, a change of
 * the filter shows the new list from its start, and a narrow window is left to scroll as it did.
 */
@Import(TestSignIn.class)
class ListHeadersBrowserIT extends IntegrationTest {

    private static final long JITA = 60003760L;

    private static Playwright playwright;
    private static Browser browser;

    @LocalServerPort
    int port;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    AssetRepository assets;

    @Autowired
    BlueprintFetcher fetcher;

    private BrowserContext context;
    private Page page;
    private final List<String> pageErrors = new ArrayList<>();

    @BeforeAll
    static void startBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch();
    }

    @AfterAll
    static void stopBrowser() {
        browser.close();
        playwright.close();
    }

    @BeforeEach
    void signInWithManyBlueprintsAndEveryKindOfAsset() {
        var sub = newSub();
        var viewer = viewerFor(sub);
        for (var feature : List.of(Feature.BLUEPRINTS)) {
            jdbc.sql("INSERT INTO account_feature (owner_account_id, feature) VALUES (:owner, :feature) ON CONFLICT DO NOTHING")
                    .param("owner", viewer.accountId().value()).param("feature", feature.name()).update();
        }
        var eve = 2_000_000_000L + ThreadLocalRandom.current().nextInt(1_000_000_000);
        var character = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO character (public_id, owner_account_id, eve_character_id, name, owner_hash, scopes, state)
                VALUES (:id, :owner, :eve, 'List Character', 'hash', :scopes, 'ACTIVE')
                """)
                .param("id", character).param("owner", viewer.accountId().value()).param("eve", eve)
                .param("scopes", new String[] {"esi-assets.read_assets.v1", "esi-characters.read_blueprints.v1"}).update();
        esi.assetsUnchanged(eve);
        // an item type each, so that the inventory has a row for each
        var types = List.of(34, 35, 36, 1001, 1002, 1003, 1004, 1005, 1006, 1007, 1011, 1012, 1013, 1014);
        assets.replace(character, IntStream.range(0, types.size())
                .mapToObj(i -> new CharacterAsset(i + 1, types.get(i), i + 1, JITA, "station", "Hangar", false, null)).toList(), Instant.now());
        // more blueprints than the first page shows, every third an original
        var ids = 87_000_000L + ThreadLocalRandom.current().nextInt(900_000) * 200L;
        esi.blueprintsOf(eve, IntStream.range(0, 130).mapToObj(i -> new CharacterBlueprint(ids + i, i % 2 == 0 ? 2001 : 2002, JITA, "Hangar",
                i % 3 == 0 ? -1 : -2, i % 11, i % 21, i % 3 == 0 ? -1 : 5)).toList());
        fetcher.refresh(viewer);
        await().atMost(10, TimeUnit.SECONDS).until(() -> !fetcher.anyRunning(viewer) && jdbc.sql("SELECT count(*) FROM character_blueprint WHERE item_id >= :id AND item_id < :id + 130")
                .param("id", ids).query(Long.class).single() == 130);
        context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1280, 450));
        page = context.newPage();
        page.onPageError(pageErrors::add);
        page.navigate("http://localhost:" + port + "/test-sign-in?sub=" + sub);
    }

    @AfterEach
    void close() {
        context.close();
        assertThat(pageErrors).isEmpty();
    }

    private double edge(String selector, String side) {
        return ((Number) page.evaluate("([selector, side]) => document.querySelector(selector).getBoundingClientRect()[side]",
                new Object[] {selector, side})).doubleValue();
    }

    private double scrolled() {
        return ((Number) page.evaluate("scrollY")).doubleValue();
    }

    private void open(String path, String table, int atLeast) {
        page.navigate("http://localhost:" + port + path);
        await().atMost(10, TimeUnit.SECONDS).until(() -> page.locator(table + " tbody tr:visible").count() >= atLeast);
        page.waitForTimeout(300);
    }

    /** Near the end of the list, so that its start is far above. Not to the very last row, where the headings leave with the end of the table. */
    private void deepInto(String table) {
        page.evaluate("table => { const rows = [...document.querySelectorAll(table + ' > tbody > tr')].filter(row => !row.hidden);"
                + " rows[rows.length - 6].scrollIntoView({block: 'center'}); }", table);
        page.waitForTimeout(200);
    }

    private void theHeaderStays(String table) {
        assertThat(edge(".list-filter", "top")).isBetween(-1.0, 1.0);
        assertThat(edge(".list-head", "top")).isBetween(edge(".list-filter", "bottom") - 1, edge(".list-filter", "bottom") + 1);
        assertThat(edge(table + " > thead th", "top")).isBetween(edge(".list-head", "bottom") - 1, edge(".list-head", "bottom") + 1);
        // and the headings are over the rows, not under them
        assertThat(page.evaluate("table => { const th = document.querySelector(table + ' > thead th:nth-child(2)'); const r = th.getBoundingClientRect();"
                + " return th.contains(document.elementFromPoint(r.left + 5, r.top + 5)); }", table)).isEqualTo(true);
    }

    private void theListIsAtItsStart(String table) {
        var headings = edge(table + " > thead th", "bottom");
        assertThat(edge(table + " > tbody > tr", "top")).isBetween(headings - 2, headings + 2);
    }

    @Test
    void deepInTheBlueprintsTheFilterTheActionsAndTheHeadingsStay() {
        open("/blueprints", "#blueprint-table", 40);

        deepInto("#blueprint-table");

        theHeaderStays("#blueprint-table");
        assertThat(page.locator("#blueprint-actions")).isInViewport();
        assertThat(page.locator("input[name=q]")).isInViewport();
        // a wide enough table and no sideways page
        assertThat(page.evaluate("document.documentElement.scrollWidth <= window.innerWidth")).isEqualTo(true);
    }

    @Test
    void aFilterOrASortChangedDeepInTheBlueprintsShowsTheNewListFromItsStart() {
        open("/blueprints", "#blueprint-table", 40);
        deepInto("#blueprint-table");
        var deep = scrolled();

        page.locator("select[name=kind]").selectOption("BPC");

        await().atMost(5, TimeUnit.SECONDS).until(() -> page.locator("#blueprint-table tbody tr").count() == 86);
        page.waitForTimeout(300);
        assertThat(scrolled()).isLessThan(deep);
        theHeaderStays("#blueprint-table");
        theListIsAtItsStart("#blueprint-table");

        deepInto("#blueprint-table");
        deep = scrolled();
        page.locator("#blueprint-table thead a", new Page.LocatorOptions().setHasText("ME")).click();
        page.waitForTimeout(800);
        assertThat(scrolled()).isLessThan(deep);
        theListIsAtItsStart("#blueprint-table");
    }

    @Test
    void deepInTheInventoryTheHeaderStaysAndOpeningARowDoesNotMoveThePage() {
        open("/inventory", "#items", 10);
        deepInto("#items");

        theHeaderStays("#items");
        var at = scrolled();
        // the row in the middle of the window: Playwright would scroll to one outside it, or under the header, before clicking it
        page.locator("#items tr[data-item]").nth(page.locator("#items tr[data-item]").count() - 6).click();
        page.waitForTimeout(800);

        assertThat(scrolled()).isEqualTo(at);
    }

    @Test
    void theListOfAFiltersChoicesFloatsOverTheRowsAndATickDeepInTheInventoryShowsTheStart() {
        open("/inventory", "#items", 10);
        deepInto("#items");
        var header = edge(".list-filter", "bottom");

        page.locator("#add-filter summary").click();
        page.locator("#add-filter a", new Page.LocatorOptions().setHasText("Category")).first().click();
        assertThat(page.locator("#filter-list")).isInViewport();

        // the header did not grow, and the count with its buttons is not covered
        assertThat(edge(".list-filter", "bottom")).isEqualTo(header);
        assertThat(edge("#filter-list", "top")).isGreaterThanOrEqualTo(edge(".list-head", "bottom") - 1);

        var deep = scrolled();
        page.locator("#filter-list label", new Page.LocatorOptions().setHasText("Ship")).locator("input").check();
        await().atMost(5, TimeUnit.SECONDS).until(() -> page.locator("#chips").count() == 1);
        page.waitForTimeout(500);
        assertThat(scrolled()).isLessThan(deep);
    }

    @Test
    void showingMoreOfTheSameListLeavesThePageWhereItIs() {
        open("/blueprints", "#blueprint-table", 100);
        page.evaluate("document.querySelector('#blueprints-show-more').scrollIntoView({block: 'center'})");
        page.waitForTimeout(200);
        var at = scrolled();

        page.locator("#blueprints-show-more").click();

        await().atMost(5, TimeUnit.SECONDS).until(() -> page.locator("#blueprint-table tbody tr").count() == 130);
        page.waitForTimeout(500);
        assertThat(scrolled()).isEqualTo(at);
    }

    @Test
    void aFiltersChoicesOpenedAtTheTopMakeRoomForThemselvesAndCoverNoRow() {
        open("/inventory", "#items", 10);

        page.locator("#add-filter summary").click();
        page.locator("#add-filter a", new Page.LocatorOptions().setHasText("Category")).first().click();
        assertThat(page.locator("#filter-list")).isVisible();
        page.waitForTimeout(300);

        assertThat(edge("#items", "top")).isGreaterThanOrEqualTo(edge("#filter-list", "bottom"));
    }

    @Test
    void aNarrowWindowScrollsEverythingAwayAsBefore() {
        page.setViewportSize(800, 450);
        open("/blueprints", "#blueprint-table", 40);

        deepInto("#blueprint-table");

        assertThat(edge(".list-filter", "bottom")).isNegative();
        assertThat(edge("#blueprint-table > thead th", "bottom")).isNegative();
    }
}
