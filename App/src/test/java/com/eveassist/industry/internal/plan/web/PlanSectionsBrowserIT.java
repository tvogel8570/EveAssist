package com.eveassist.industry.internal.plan.web;

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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/**
 * The plan page's sections, signed in and tried without an account: only the products are open at first, and what the user opened stays open
 * when an edit draws the section again and after a reload.
 */
@Import(TestSignIn.class)
class PlanSectionsBrowserIT extends IntegrationTest {

    private static final List<String> REDRAWN = List.of("parts-section", "runs-section", "materials-section");

    private static Playwright playwright;
    private static Browser browser;

    @LocalServerPort
    int port;

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
    void open() {
        context = browser.newContext();
        page = context.newPage();
        page.onPageError(pageErrors::add);
    }

    @AfterEach
    void close() {
        context.close();
        assertThat(pageErrors).isEmpty();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private void planWithAHauler() {
        page.navigate(url("/test-sign-in?sub=" + newSub()));
        page.navigate(url("/plans"));
        page.fill("#new-plan-name", "Sections");
        page.click("#new-plan");
        page.waitForURL("**/plans/*");
        addAHauler();
    }

    private void addAHauler() {
        page.locator("#product-search").pressSequentially("hauler");
        page.locator("#search-results button", new Page.LocatorOptions().setHasText("Test Hauler")).first().click();
        page.locator("#products td", new Page.LocatorOptions().setHasText("Test Hauler")).first().waitFor();
        page.waitForLoadState();
    }

    private boolean isOpen(String id) {
        return (Boolean) page.locator("#" + id).evaluate("details => details.open");
    }

    private void onlyTheProductsAreOpen() {
        assertThat(isOpen("products-section")).isTrue();
        for (var id : REDRAWN) {
            assertThat(isOpen(id)).as(id).isFalse();
            assertThat(page.locator("#" + id + " > summary > .chevron")).hasCount(1);
        }
    }

    @Test
    void onlyTheProductsAreOpenAtFirstAndTheHeadersSayHowMuchIsInside() {
        planWithAHauler();

        onlyTheProductsAreOpen();
        assertThat(page.locator("#products-count")).hasText("1");
        assertThat(page.locator("#parts-section > summary .badge").first()).hasText("1");
        // room under what an open section holds
        assertThat((Number) page.locator("#products-section").evaluate("""
                details => Math.round(details.getBoundingClientRect().bottom
                        - Math.max(...[...details.querySelectorAll('input, td')].map(e => e.getBoundingClientRect().bottom)))"""))
                .extracting(Number::intValue).satisfies(room -> assertThat(room).isGreaterThanOrEqualTo(14));
        assertThat(page.locator("#product-search")).isVisible();
    }

    @Test
    void anOpenedSectionStaysOpenWhenAnEditDrawsItAgainAndAfterAReload() {
        planWithAHauler();
        page.click("#parts-section > summary");
        page.click("#materials-section > summary");

        // buying the part draws the three sections again
        page.click("#buy-1002");

        assertThat(page.locator("#buy-1002")).hasAttribute("aria-pressed", "true");
        page.waitForTimeout(500);
        assertThat(isOpen("parts-section")).isTrue();
        assertThat(isOpen("materials-section")).isTrue();
        assertThat(isOpen("runs-section")).isFalse();
        assertThat(page.locator("#materials-section table")).isVisible();
        // the bought part has no price in the test data and the materials have: the warning is on the section that holds the item
        assertThat(page.locator("#parts-section > summary .badge-warning")).hasText("Check prices");
        assertThat(page.locator("#materials-section > summary .badge-warning")).hasCount(0);

        page.reload();

        assertThat(isOpen("parts-section")).isTrue();
        assertThat(isOpen("runs-section")).isFalse();
    }

    @Test
    void aTriedPlanHasTheSameSectionsAndKeepsThemThroughAnEdit() {
        page.navigate(url("/try"));
        addAHauler();
        onlyTheProductsAreOpen();
        page.click("#runs-section > summary");

        page.fill("#quantity-1001", "3");
        page.press("#quantity-1001", "Tab");
        page.waitForTimeout(500);
        assertThat(isOpen("runs-section")).isTrue();

        addAHauler();
        assertThat(page.locator("#products-count")).hasText("1");
        assertThat(isOpen("runs-section")).isTrue();
        assertThat(isOpen("parts-section")).isFalse();
    }
}
