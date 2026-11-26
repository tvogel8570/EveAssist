package com.eveassist.industry.internal.project.web;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.eveassist.IntegrationTest;
import com.eveassist.identity.internal.TestSignIn;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
 * The project's header, which stays in view with the state, the figures and the action, the place on the page, which a save keeps, and the
 * colours that tell a plan from a project.
 */
@Import(TestSignIn.class)
class ProjectHeaderBrowserIT extends IntegrationTest {

    private static Playwright playwright;
    private static Browser browser;

    @LocalServerPort
    int port;

    @Autowired
    JdbcClient jdbc;

    private BrowserContext context;
    private Page page;
    private final List<String> pageErrors = new ArrayList<>();
    private String sub;

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
    void openAProjectWithItsSectionsOpen() {
        sub = newSub();
        context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1200, 600));
        page = context.newPage();
        page.onPageError(pageErrors::add);
        page.navigate(url("/test-sign-in?sub=" + sub));
        page.navigate(url("/plans"));
        page.fill("#new-plan-name", "Header plan");
        page.click("#new-plan");
        page.waitForURL("**/plans/*");
        for (var product : List.of(new String[] {"hauler", "Test Hauler"}, new String[] {"nested", "Nested Ship"})) {
            page.locator("#product-search").fill("");
            page.locator("#product-search").pressSequentially(product[0]);
            page.locator("#search-results button", new Page.LocatorOptions().setHasText(product[1])).first().click();
            page.locator("#products td", new Page.LocatorOptions().setHasText(product[1])).first().waitFor();
            page.waitForLoadState();
        }
        page.navigate(url("/projects"));
        page.click("#start-project");
        page.click("#start-project-confirm");
        page.waitForURL("**/projects/*");
        for (var section : List.of("settings", "items", "jobs", "delivered-cost")) {
            page.click("#" + section + " > summary");
        }
        page.waitForFunction("localStorage.getItem('eveassist.section.delivered-cost') === 'open'");
    }

    @AfterEach
    void close() {
        context.close();
        assertThat(pageErrors).isEmpty();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private double top(String selector) {
        return ((Number) page.evaluate("selector => document.querySelector(selector).getBoundingClientRect().top", selector)).doubleValue();
    }

    private void scrollSoThatItIs(String selector, int pixelsDown) {
        page.evaluate("([selector, down]) => window.scrollTo(0, document.querySelector(selector).getBoundingClientRect().top + scrollY - down)",
                new Object[] {selector, pixelsDown});
    }

    /** Makes a change that reloads the page, and waits for the page to be back where it puts itself. */
    private void saving(Runnable change) {
        page.waitForNavigation(change);
        page.waitForLoadState();
        page.waitForTimeout(300);
    }

    @Test
    void theHeaderStaysInViewWithTheStateTheFiguresAndTheAction() {
        // well down the page, past the top of both columns
        scrollSoThatItIs("#jobs", 200);

        assertThat(top("#project-header")).isBetween(-1.0, 1.0);
        var header = page.locator("#project-header");
        assertThat(header).containsText("Header plan");
        assertThat(header.locator("#state-badge")).isVisible();
        assertThat(header.locator("#header-cost")).containsText("ISK");
        assertThat(header.locator("#header-quote")).containsText("ISK");
        assertThat(header.locator("button[id^=cost-it]")).isVisible();
        // the quote card stays clear of the header
        assertThat(top("#quote-card")).isGreaterThanOrEqualTo(((Number) page.evaluate(
                "document.querySelector('#project-header').getBoundingClientRect().bottom")).doubleValue() - 1);
    }

    @Test
    void aSaveKeepsThePlaceOnThePageAlsoWhenTheCostingGoesOutOfDateAndWhenItIsCostedAgain() {
        scrollSoThatItIs("#quantity-1001", 350);
        var before = top("#quantity-1001");

        saving(() -> {
            page.fill("#quantity-1001", "3");
            page.press("#quantity-1001", "Tab");
        });

        assertThat(page.locator("#banner-stale")).isVisible();
        assertThat(top("#quantity-1001")).isBetween(before - 3, before + 3);
        // and the figures of the costing that is out of date are dimmed
        assertThat(page.locator("#header-figures")).hasClass(java.util.regex.Pattern.compile(".*opacity-50.*"));

        // clicked in the page: Playwright's own click scrolls the page first for an element that stays in view, which a user's click does not
        saving(() -> page.evaluate("document.querySelector('#project-header #cost-it').click()"));

        assertThat(page.locator("#banner-stale")).hasCount(0);
        assertThat(top("#quantity-1001")).isBetween(before - 3, before + 3);
    }

    @Test
    void aSaveFromASectionKeepsThePlaceAndAnotherPageOpensAtItsTop() {
        scrollSoThatItIs("#save-settings", 300);
        var before = top("#save-settings");

        saving(() -> page.click("#save-settings"));

        assertThat(top("#save-settings")).isBetween(before - 3, before + 3);
        page.navigate(url("/projects"));
        assertThat(((Number) page.evaluate("scrollY")).intValue()).isZero();
    }

    @Test
    void aQuotedProjectOffersTheNewVersionFromTheHeader() {
        var owner = viewerFor(sub).accountId().value();
        jdbc.sql("INSERT INTO facility (public_id, owner_account_id, name) VALUES (:id, :owner, 'Azbel')")
                .param("id", UUID.randomUUID()).param("owner", owner).update();
        page.reload();
        page.selectOption("#default-facility", new com.microsoft.playwright.options.SelectOption().setLabel("Azbel"));
        saving(() -> page.click("#save-settings"));
        saving(() -> page.click("#project-header #cost-it"));
        saving(() -> page.click("#issue-quote"));
        assertThat(page.locator("#project-header #banner-quoted")).isVisible();

        saving(() -> page.click("#new-version-header"));

        assertThat(page.locator("#banner-quoted")).hasCount(0);
        assertThat(page.locator("#quantity-1001")).isEnabled();
    }

    private String colourOf(String selector, String property) {
        return (String) page.locator(selector).first().evaluate("(element, property) => getComputedStyle(element)[property]", property);
    }

    @Test
    void theMenuAndTheTitleTellAProjectFromAPlanByWordAndColour() {
        var projectEntry = colourOf("#nav-projects", "color");
        var projectLine = colourOf("#nav-projects", "borderBottomColor");
        assertThat(projectLine).isEqualTo(projectEntry);
        assertThat(projectEntry).isNotEqualTo(colourOf("#nav-plans", "color"));
        assertThat(colourOf(".area-title", "borderLeftColor")).isEqualTo(projectEntry);
        assertThat(page.locator(".area-kind")).hasText("Project");

        page.navigate(url("/plans"));
        page.locator("a[href^='/plans/']").first().click();
        page.waitForURL("**/plans/*");

        var planEntry = colourOf("#nav-plans", "color");
        assertThat(planEntry).isNotEqualTo(projectEntry);
        assertThat(colourOf(".area-title", "borderLeftColor")).isEqualTo(planEntry);
        assertThat(page.locator(".area-kind")).hasText("Plan");
    }
}
