package com.eveassist.inventory.internal;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.eveassist.IntegrationTest;
import com.eveassist.esi.CharacterAsset;
import com.eveassist.identity.internal.TestSignIn;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The inventory page in a real browser, where htmx, the address and the menus have to work together. */
@Import(TestSignIn.class)
class InventoryBrowserIT extends IntegrationTest {

    private static final long JITA = 60003760L;
    private static final long AMARR = 60008494L;

    private static Playwright playwright;
    private static Browser browser;

    @LocalServerPort
    int port;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    AssetRepository assets;

    private BrowserContext context;
    private Page page;
    private String sub;
    private final List<String> pageErrors = new ArrayList<>();
    private UUID one;
    private UUID two;

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
    void signInWithTwoCharactersAndSomeAssets() {
        sub = newSub();
        var viewer = viewerFor(sub);
        one = link(viewer, "Alt One");
        two = link(viewer, "Alt Two");
        assets.replace(one, List.of(asset(1, 34, 1_500, JITA), asset(2, 1001, 1, AMARR)), Instant.now());
        assets.replace(two, List.of(asset(1, 34, 500, JITA), asset(2, 35, 20, JITA), asset(3, 1002, 5, AMARR)), Instant.now());
        context = browser.newContext();
        page = context.newPage();
        page.onPageError(pageErrors::add);
        page.navigate(url("/test-sign-in?sub=" + sub));
    }

    @AfterEach
    void close() {
        context.close();
        assertThat(pageErrors).isEmpty();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private UUID link(com.eveassist.identity.Viewer viewer, String name) {
        var id = UUID.randomUUID();
        var eve = 2_000_000_000L + ThreadLocalRandom.current().nextInt(1_000_000_000);
        jdbc.sql("""
                INSERT INTO character (public_id, owner_account_id, eve_character_id, name, owner_hash, scopes, state)
                VALUES (:id, :owner, :eve, :name, 'hash', :scopes, 'ACTIVE')
                """)
                .param("id", id).param("owner", viewer.accountId().value()).param("eve", eve).param("name", name)
                .param("scopes", new String[] {"esi-assets.read_assets.v1"}).update();
        esi.assetsUnchanged(eve);
        return id;
    }

    private static CharacterAsset asset(long itemId, int typeId, long quantity, long location) {
        return new CharacterAsset(itemId, typeId, quantity, location, "station", "Hangar", false, null);
    }

    private void open() {
        page.navigate(url("/inventory"));
        assertThat(page.locator("#summary")).containsText("4 item types");
    }

    private void addFilter(String column) {
        page.locator("#add-filter summary").click();
        page.locator("#add-filter a", new Page.LocatorOptions().setHasText(Pattern.compile("^" + column + "$"))).click();
        assertThat(page.locator("#filter-list")).isVisible();
    }

    @Test
    void addFilterOpensAColumnsListAndTickingFiltersTheListAndTheAddress() {
        open();

        addFilter("Category");
        page.locator("#filter-list label", new Page.LocatorOptions().setHasText("Material")).locator("input").check();

        assertThat(page.locator("#summary")).containsText("2 item types");
        assertThat(page.locator("#items")).containsText("Tritanium");
        assertThat(page.locator("#items")).not().containsText("Test Hauler");
        assertThat(page.locator("#chips")).containsText("Category: Material");
        assertThat(page.url()).contains("category=4");
        // the list stays open so another value can be ticked, and nothing was sent twice
        assertThat(page.locator("#filter-list")).isVisible();
        assertThat(page.locator("#filter-state input[name=category]")).hasCount(0);
    }

    @Test
    void filtersFromDifferentColumnsCombineAndEachCanBeTakenAwayAlone() {
        open();
        addFilter("Category");
        page.locator("#filter-list label", new Page.LocatorOptions().setHasText("Material")).locator("input").check();
        addFilter("Character");
        page.locator("#filter-list label", new Page.LocatorOptions().setHasText("Alt Two")).locator("input").check();

        assertThat(page.locator("#summary")).containsText("2 item types, 520 units");
        assertThat(page.locator("#chips")).containsText("Category: Material");
        assertThat(page.locator("#chips")).containsText("Character: Alt Two");
        assertThat(page.url()).contains("category=4").contains("character=" + two);
        // the first list was replaced by the second, and its filter is still on
        assertThat(page.locator("#filter-list strong")).hasText("Character");

        page.locator("#chips a[aria-label^='Remove the filter Character']").click();

        assertThat(page.locator("#summary")).containsText("2 item types, 2,020 units");
        assertThat(page.locator("#chips")).not().containsText("Character");
        assertThat(page.locator("#chips")).containsText("Category: Material");
        assertThat(page.locator("#filter-list")).hasCount(0);
        assertThat(page.url()).doesNotContain("character=");
    }

    @Test
    void doneClosesTheListAndKeepsTheFilter() {
        open();
        addFilter("Category");
        page.locator("#filter-list label", new Page.LocatorOptions().setHasText("Ship")).locator("input").check();

        page.locator("#filter-done").click();

        assertThat(page.locator("#filter-list")).hasCount(0);
        assertThat(page.locator("#summary")).containsText("1 item type");
        assertThat(page.locator("#chips")).containsText("Category: Ship");
        // the filter is still on after the list closed, so a later change keeps it
        page.locator("#show-characters").uncheck();
        assertThat(page.locator("#chips")).containsText("Category: Ship");
        assertThat(page.url()).contains("category=6");
    }

    @Test
    void typingInTheSearchFiltersAfterAPauseAndItsPartOfTheAddress() {
        open();

        page.locator("#inventory-search").fill("trit");

        assertThat(page.locator("#summary")).containsText("1 item type");
        assertThat(page.locator("#items")).containsText("Tritanium");
        assertThat(page.url()).contains("q=trit");
        page.locator("#inventory-search").fill("");
        assertThat(page.locator("#summary")).containsText("4 item types");
    }

    @Test
    void theAddressRestoresTheViewOnReloadAndTheBackButtonGoesBackOneStep() {
        open();
        addFilter("Category");
        page.locator("#filter-list label", new Page.LocatorOptions().setHasText("Material")).locator("input").check();
        assertThat(page.locator("#summary")).containsText("2 item types");

        page.reload();

        assertThat(page.locator("#summary")).containsText("2 item types");
        assertThat(page.locator("#chips")).containsText("Category: Material");
        assertThat(page.locator("#filter-list")).hasCount(0);

        page.goBack();

        assertThat(page.locator("#summary")).containsText("4 item types");
        assertThat(page.locator("#chips")).hasCount(0);
    }

    @Test
    void aRowOpensToShowWhereAndClosesAgain() {
        open();
        var row = page.locator("tr[data-item='Tritanium']");

        row.click();

        var detail = page.locator("#where-34");
        assertThat(detail).isVisible();
        assertThat(detail).containsText("Jita IV - Moon 4 - Caldari Navy Assembly Plant");
        assertThat(detail).containsText("Alt One 1,500, Alt Two 500");
        assertThat(row.locator("[data-expander]")).hasText("▾");

        row.click();

        assertThat(detail).isHidden();
        // opening it again does not ask again: it is already there
        row.click();
        assertThat(detail).isVisible();
    }

    @Test
    void anOpenRowShowsOnlyWhatTheFilterLetsThrough() {
        open();
        addFilter("Character");
        page.locator("#filter-list label", new Page.LocatorOptions().setHasText("Alt Two")).locator("input").check();
        assertThat(page.locator("#summary")).containsText("3 item types");

        page.locator("tr[data-item='Tritanium']").click();

        assertThat(page.locator("#where-34")).containsText("500");
        assertThat(page.locator("#where-34")).not().containsText("1,500");
    }

    @Test
    void theCharactersCanBeHiddenAndTheBarsGoWithThem() {
        open();
        assertThat(page.locator("#items svg:visible").first()).isVisible();

        page.locator("#show-characters").uncheck();

        assertThat(page.locator("#items svg")).hasCount(0);
        assertThat(page.locator("#summary")).containsText("4 item types");
        assertThat(page.url()).doesNotContain("chars=1");
        page.locator("#show-characters").check();
        assertThat(page.locator("#items svg:visible").first()).isVisible();
    }

    @Test
    void sortingChangesTheOrderOfTheList() {
        open();
        assertThat(page.locator("tr[data-item]").first()).hasAttribute("data-item", "Tritanium");

        page.locator("#inventory-sort").selectOption("name");

        assertThat(page.locator("tr[data-item]").first()).hasAttribute("data-item", "Pilot's Mineral");
        assertThat(page.url()).contains("sort=name");
    }

    @Test
    void theMinimumIsAskedAboutWhenTheNumberIsEntered() {
        open();
        addFilter("Total at least");

        page.locator("#minimum-input").fill("100");
        page.locator("#minimum-input").press("Enter");

        assertThat(page.locator("#summary")).containsText("1 item type, 2,000 units");
        assertThat(page.locator("#chips")).containsText("Total at least: 100");
    }

    @Test
    void theHowListHasAPresetThatLeavesOutFittedModulesAndShipHolds() {
        // two items fitted or in a hold, in a ship that is in Jita
        assets.replace(one, List.of(asset(1, 34, 1_500, JITA), asset(2, 1001, 1, AMARR),
                new CharacterAsset(3, 35, 9, 2, "item", "HiSlot0", false, null),
                new CharacterAsset(4, 1002, 8, 2, "item", "Cargo", false, null)), Instant.now());
        page.navigate(url("/inventory"));
        assertThat(page.locator("#summary")).containsText("units");
        addFilter("How held");

        page.locator("#filter-list a", new Page.LocatorOptions().setHasText("Not fitted or in ships")).click();

        assertThat(page.locator("#chips")).containsText("How held:");
        assertThat(page.locator("#summary")).containsText("units");
        assertThat(page.url()).contains("how=HANGAR");
        page.locator("tr[data-item=\"Pilot's Mineral\"]").click();
        assertThat(page.locator("#where-35")).not().containsText("fitted");
        assertThat(page.locator("#where-35")).containsText("20");
        assertThat(page.locator("#where-35")).not().containsText("29");
    }

    @Test
    void theListRefreshesByItselfWhenAFetchFinishes() {
        var viewer = viewerFor(sub);
        var late = link(viewer, "Late One");
        var lateEve = jdbc.sql("SELECT eve_character_id FROM character WHERE public_id = :id").param("id", late)
                .query(Long.class).single();
        var gate = new CountDownLatch(1);
        esi.holdAssetCalls(gate);
        esi.assetsOf(lateEve, List.of(asset(1, 36, 12_345, JITA)));

        page.navigate(url("/inventory"));
        assertThat(page.locator("#fetch-status")).containsText("updating");
        assertThat(page.locator("tr[data-item='Unpriced Ore']")).hasCount(0);

        gate.countDown();

        // no reload: the status block asks until the fetch is done, and then the list asks again
        assertThat(page.locator("tr[data-item='Unpriced Ore']")).isVisible();
        assertThat(page.locator("#fetch-status")).not().containsText("updating");
    }

    @Test
    void theStatusOpensIntoATableWithEachCharacter() {
        open();

        page.locator("#fetch-status summary").click();

        assertThat(page.locator("#fetch-status table")).containsText("Alt One");
        assertThat(page.locator("#fetch-status table")).containsText("Alt Two");
    }

    @Test
    void exportAndCopyAreOfferedAndTheExportFollowsTheFilter() {
        open();
        addFilter("Category");
        page.locator("#filter-list label", new Page.LocatorOptions().setHasText("Ship")).locator("input").check();
        assertThat(page.locator("#summary")).containsText("1 item type");

        assertThat(page.locator("#export-csv")).hasAttribute("href", Pattern.compile(".*category=6.*"));
        assertThat(page.locator("#copy-lines")).isVisible();
    }
}
