package com.eveassist.industry.internal.tryplan.web;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.eveassist.IntegrationTest;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Route;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;

/** A plan without an account in a real browser, where it has to survive a reload with nothing stored on the server. */
class TryPlanBrowserIT extends IntegrationTest {

    private static Playwright playwright;
    private static Browser browser;

    @LocalServerPort
    int port;

    @Autowired
    JdbcClient jdbc;

    private BrowserContext context;
    private Page page;
    private final List<String> pageErrors = new ArrayList<>();
    private final List<String> requests = new ArrayList<>();

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
    void visit() {
        jdbc.sql("DELETE FROM jita_order").update();
        jdbc.sql("DELETE FROM jita_fetch").update();
        context = browser.newContext();
        // these tests are about what is in the sections, so all are open, as a user who opened them has it (PlanSectionsBrowserIT is about that)
        context.addInitScript("['plan-parts', 'plan-runs', 'plan-materials'].forEach(name => localStorage.setItem('eveassist.section.' + name, 'open'))");
        page = context.newPage();
        page.onPageError(pageErrors::add);
        page.onRequest(request -> requests.add(request.method() + " " + request.url()));
        page.navigate("http://localhost:" + port + "/try");
    }

    @AfterEach
    void close() {
        context.close();
        assertThat(pageErrors).isEmpty();
    }

    private void addHauler() {
        page.locator("#product-search").pressSequentially("hauler");
        page.locator("#search-results button", new Page.LocatorOptions().setHasText("Test Hauler")).click();
        page.locator("#products td", new Page.LocatorOptions().setHasText("Test Hauler")).waitFor();
    }

    @Test
    void aVisitorCanBuildACostedPlanWithoutSigningInAndGetsNoRefreshOrSave() {
        addHauler();

        assertThat(page.locator("#plan-total-value")).hasText("630 ISK");
        assertThat(page.locator("#run-1001")).hasCount(1);
        assertThat(page.locator("#refresh-prices")).hasCount(0);
        assertThat(page.locator("#products")).containsText("Test Hauler");
        assertThat(page.locator("#search-results button")).hasCount(0);
        assertThat(requests.stream().filter(request -> request.contains("/plans"))).isEmpty();
    }

    @Test
    void thePlanSurvivesAReloadFromTheBrowserAlone() {
        int plansBefore = jdbc.sql("SELECT count(*) FROM plan").query(Integer.class).single();
        addHauler();
        page.fill("#quantity-1001", "2");
        page.press("#quantity-1001", "Tab");
        assertThat(page.locator("#plan-total-value")).hasText("1,260 ISK");
        page.click("#buy-1002");
        assertThat(page.locator("#buy-1002")).hasAttribute("aria-pressed", "true");

        page.reload();

        assertThat(page.locator("#quantity-1001")).hasValue("2");
        assertThat(page.locator("#buy-1002")).hasAttribute("aria-pressed", "true");
        assertThat(page.locator("#try-state")).hasValue("p=1001:2;b=1002");
        assertThat(jdbc.sql("SELECT count(*) FROM plan").query(Integer.class).single()).isEqualTo(plansBefore);
    }

    /**
     * htmx starts on a timer, which can fire while a later script is still loading. app.js has to put the kept plan in the
     * page before then, or the empty plan is sent and its answer is kept over the real one. The answer is held back too,
     * so that it arrives after app.js has started, which is when the kept plan was lost.
     */
    @Test
    void thePlanSurvivesAReloadWhenAppJsArrivesLate() {
        addHauler();
        page.fill("#quantity-1001", "2");
        page.press("#quantity-1001", "Tab");
        assertThat(page.locator("#plan-total-value")).hasText("1,260 ISK");
        page.route("**/js/app.js", route -> resumeAfter(route, 300));
        page.route("**/try/render", route -> resumeAfter(route, 800));

        page.reload();

        assertThat(page.locator("#quantity-1001")).hasValue("2");
        assertThat(page.evaluate("() => localStorage.getItem('eveassist.try.plan')"))
                .isEqualTo("p=1001:2;b=");
    }

    private static void resumeAfter(Route route, int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        route.resume();
    }

    @Test
    void removeAndUndoWork() {
        addHauler();

        page.click("#remove-1001");
        assertThat(page.locator("#no-products")).hasCount(1);
        assertThat(page.locator("#undo-remove")).hasCount(1);

        page.click("#undo-remove");
        assertThat(page.locator("#quantity-1001")).hasValue("1");
        assertThat(page.locator("#plan-total-value")).hasText("630 ISK");
    }

    @Test
    void startOverClearsThePlanAndWhatTheBrowserKept() {
        addHauler();

        page.click("#try-start-over");
        assertThat(page.locator("#no-products")).hasCount(1);
        page.reload();

        assertThat(page.locator("#no-products")).hasCount(1);
        assertThat(page.locator("#try-state")).hasValue("p=;b=");
    }

    @Test
    void aDamagedSavedPlanIsIgnored() {
        page.evaluate("localStorage.setItem('eveassist.try.plan', 'garbage')");

        page.reload();

        assertThat(page.locator("#no-products")).hasCount(1);
        assertThat(page.locator("#error-toast")).isHidden();
    }

    @Test
    void theBlockedUnitsMessageStillWorksOnThisPage() {
        addHauler();

        page.fill("#quantity-1001", "0");
        page.press("#quantity-1001", "Tab");

        assertThat(page.locator("#error-toast")).containsText("at least 1");
        assertThat(page.locator("#quantity-1001")).hasValue("1");
    }
}
