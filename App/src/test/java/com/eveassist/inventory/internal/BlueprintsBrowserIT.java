package com.eveassist.inventory.internal;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.eveassist.IntegrationTest;
import com.eveassist.characters.Feature;
import com.eveassist.esi.CharacterBlueprint;
import com.eveassist.identity.internal.TestSignIn;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
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

/** The Blueprints page in a real browser, where htmx, the address and the drawer have to work together. */
@Import(TestSignIn.class)
class BlueprintsBrowserIT extends IntegrationTest {

    private static Playwright playwright;
    private static Browser browser;

    @LocalServerPort
    int port;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    BlueprintFetcher fetcher;

    private long eveId;
    private BrowserContext context;
    private Page page;
    private String sub;
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
    void signInWithABlueprint() {
        sub = newSub();
        var viewer = viewerFor(sub);
        jdbc.sql("INSERT INTO account_feature (owner_account_id, feature) VALUES (:owner, :feature) ON CONFLICT DO NOTHING")
                .param("owner", viewer.accountId().value()).param("feature", Feature.BLUEPRINTS.name()).update();
        var eve = 2_000_000_000L + ThreadLocalRandom.current().nextInt(1_000_000_000);
        eveId = eve;
        jdbc.sql("""
                INSERT INTO character (public_id, owner_account_id, eve_character_id, name, owner_hash, scopes, state)
                VALUES (:id, :owner, :eve, 'Drawer Character', 'hash', :scopes, 'ACTIVE')
                """)
                .param("id", UUID.randomUUID()).param("owner", viewer.accountId().value()).param("eve", eve)
                .param("scopes", new String[] {"esi-characters.read_blueprints.v1"}).update();
        esi.blueprintsOf(eve, List.of(new CharacterBlueprint(86001, 2001, 60003760L, "Hangar", -1, 4, 8, -1)));
        // the read is done before the page opens, so that the table is there without waiting for the page's own read
        fetcher.refresh(viewer);
        await().atMost(10, TimeUnit.SECONDS).until(() -> !fetcher.anyRunning(viewer)
                && jdbc.sql("SELECT count(*) FROM character_blueprint WHERE item_id = 86001")
                        .query(Long.class).single() > 0);
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

    @Test
    void theDrawerOpensOnAClickAndClosesWithoutLosingThePage() {
        page.navigate(url("/blueprints"));
        // the page starts the read when it is on screen and swaps the table in when the read is done
        var link = page.locator("[data-blueprint-link]").first();
        assertThat(link).isVisible();
        assertThat(page.locator("#blueprint-actions")).hasCount(1);

        link.click();
        assertThat(page.locator("body.blueprint-drawer-open")).hasCount(1);
        assertThat(page.locator("#blueprint-drawer h2")).containsText("Test Hauler Blueprint");
        assertThat(page).hasURL(Pattern.compile(".*/blueprints/86001\\?.*"));

        page.keyboard().press("Escape");
        assertThat(page.locator("body.blueprint-drawer-open")).hasCount(0);
        assertThat(page).hasURL(Pattern.compile(".*/blueprints\\?.*"));
        // the layout, the filters and the table are all still there
        assertThat(page.locator("h1")).containsText("Blueprints");
        assertThat(page.locator("#blueprint-filters")).isVisible();
        assertThat(page.locator("#blueprint-table")).isVisible();

        // going back and forward asks the server for whole pages, never a bare fragment over the body
        page.goBack();
        assertThat(page.locator("#blueprint-filters")).isVisible();
        assertThat(page.locator("#blueprint-table")).isVisible();
    }

    @Test
    void theBackButtonRestoresTheWholePageWithoutAReloadFirst() {
        page.navigate(url("/blueprints"));
        assertThat(page.locator("[data-blueprint-link]").first()).isVisible();
        page.locator("select[name=kind]").selectOption("BPC");
        assertThat(page.locator("#blueprint-results")).containsText("No blueprints match");
        assertThat(page).hasURL(Pattern.compile(".*kind=BPC.*"));

        // htmx puts the earlier step back itself, by asking the server for its address: the answer must be the page
        page.goBack();

        assertThat(page.locator("[data-blueprint-link]").first()).isVisible();
        assertThat(page.locator("select[name=kind]")).hasValue("");
        assertThat(page.locator("#nav-blueprints")).isVisible();
        assertThat(page.locator("#blueprint-filters")).isVisible();
        assertThat(page.locator("#blueprint-status")).isVisible();

        page.goForward();

        assertThat(page.locator("select[name=kind]")).hasValue("BPC");
        assertThat(page.locator("#blueprint-results")).containsText("No blueprints match");
        assertThat(page.locator("#nav-blueprints")).isVisible();
    }

    @Test
    void theTableAppearsWhenTheReadFinishesWithoutReloadingThePage() {
        // the page opens with nothing stored; its own read fills the table and the page has to show it by itself
        jdbc.sql("DELETE FROM character_blueprint WHERE item_id = 86001").update();
        page.navigate(url("/blueprints"));
        assertThat(page.locator("#blueprint-table")).hasCount(0);
        assertThat(page.locator("[data-blueprint-link]").first()).isVisible();
    }

    @Test
    void showMoreAddsAPageAndTheStatusTableStaysOpenWhileItIsRefreshed() {
        var many = new java.util.ArrayList<CharacterBlueprint>();
        for (var i = 0; i < 230; i++) {
            many.add(new CharacterBlueprint(91_000L + i, 2001, 60003760L, "Hangar", -1, 4, 8, -1));
        }
        esi.blueprintsOf(eveId, many);
        fetcher.refresh(viewerFor(sub));
        await().atMost(10, TimeUnit.SECONDS).until(() -> !fetcher.anyRunning(viewerFor(sub))
                && jdbc.sql("SELECT count(*) FROM character_blueprint WHERE item_id = 91000").query(Long.class).single() > 0);
        page.navigate(url("/blueprints"));

        assertThat(page.locator("[data-blueprint-row]")).hasCount(100);
        assertThat(page.locator("#blueprint-results")).containsText("Showing 100 of 230 blueprints");
        page.locator("#blueprints-show-more").click();
        assertThat(page.locator("[data-blueprint-row]")).hasCount(200);
        assertThat(page).hasURL(Pattern.compile(".*limit=200.*"));
        page.locator("#blueprints-show-more").click();
        assertThat(page.locator("[data-blueprint-row]")).hasCount(230);
        assertThat(page.locator("#blueprints-show-more")).hasCount(0);

        // the holder table is closed until it is asked for, and a refresh of the status line does not shut it
        assertThat(page.locator("#blueprint-status-details")).not().hasAttribute("open", Pattern.compile(".*"));
        page.locator("#blueprint-status-details summary").click();
        assertThat(page.locator("#blueprint-status-details table")).isVisible();
        page.evaluate("htmx.ajax('GET', '/blueprints/status', {target: '#blueprint-status', swap: 'outerHTML'})");
        page.waitForTimeout(500);
        assertThat(page.locator("#blueprint-status-details table")).isVisible();
    }

    @Test
    void theTickBoxesAreSquaresAndNotCircles() {
        page.navigate(url("/blueprints"));
        assertThat(page.locator("[data-blueprint-pick]").first()).isVisible();
        // a corner radius of half the width or more is a circle, which reads as a radio button
        for (var selector : List.of("[data-blueprint-pick]", "#blueprint-pick-all")) {
            var box = page.locator(selector).first();
            var rounded = (Boolean) box.evaluate("""
                    e => parseFloat(getComputedStyle(e).borderTopLeftRadius) >= e.getBoundingClientRect().width / 2""");
            assertThat(rounded).as(selector + " is round").isFalse();
        }
    }

    @Test
    void aVisitThatChangedNothingDoesNotFetchTheTableAgain() throws Exception {
        var tables = new java.util.concurrent.atomic.AtomicInteger();
        var statuses = new java.util.concurrent.atomic.AtomicInteger();
        page.route(address -> address.contains("/blueprints"), route -> {
            var request = route.request();
            if (request.url().contains("/blueprints/status")) {
                statuses.incrementAndGet();
            } else if (request.headers().containsKey("hx-request")) {
                tables.incrementAndGet();
            }
            route.resume();
        });
        page.navigate(url("/blueprints"));
        assertThat(page.locator("[data-blueprint-link]").first()).isVisible();
        // the page asks for its read when it has loaded; the read ends with nothing new, so the table stays as it is
        await().atMost(5, TimeUnit.SECONDS).until(() -> statuses.get() >= 1);
        // the status block has nothing to wait for once the read is done, and its last answer could have told the table
        // to look again: a moment more makes sure that nothing follows
        assertThat(page.locator("#blueprint-status[hx-trigger]")).hasCount(0,
                new com.microsoft.playwright.assertions.LocatorAssertions.HasCountOptions().setTimeout(20_000));
        Thread.sleep(1_500);
        assertThat(statuses.get()).as("the read was asked for").isGreaterThanOrEqualTo(1);
        assertThat(tables.get()).as("the table was fetched again although nothing changed").isZero();
    }
}
