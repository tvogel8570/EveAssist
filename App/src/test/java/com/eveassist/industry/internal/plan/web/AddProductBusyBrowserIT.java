package com.eveassist.industry.internal.plan.web;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.eveassist.IntegrationTest;
import com.eveassist.identity.internal.TestSignIn;
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
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/**
 * Adding a product can wait on prices from ESI. While it does, the page shows a wait cursor, the search results cannot be chosen, and a
 * second choice sends nothing, which would add the product twice.
 */
@Import(TestSignIn.class)
class AddProductBusyBrowserIT extends IntegrationTest {

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

    private void search() {
        page.locator("#product-search").pressSequentially("hauler");
        page.locator("#search-results button", new Page.LocatorOptions().setHasText("Test Hauler")).waitFor();
    }

    private String cursor() {
        return (String) page.evaluate("getComputedStyle(document.body).cursor");
    }

    private void chooseTheFirstResultAgain() {
        page.evaluate("document.querySelector('#search-list form').requestSubmit()");
    }

    @Test
    void aSignedInPlanWaitsVisiblyAndSendsOneRequest() {
        page.navigate(url("/test-sign-in?sub=" + newSub()));
        page.navigate(url("/plans"));
        page.fill("#new-plan-name", "Busy plan");
        page.click("#new-plan");
        page.waitForURL("**/plans/*");
        search();
        assertThat(cursor()).isNotEqualTo("wait");
        // the form posts and leaves the page: keep the page here, and count what would have been sent
        page.evaluate("window.sent = 0; window.addEventListener('submit', event => { window.sent++; event.preventDefault(); })");

        page.locator("#search-results button").first().click();

        assertThat(cursor()).isEqualTo("wait");
        assertThat(page.locator("#search-results button").first()).isDisabled();
        assertThat(page.locator("#search-results .loading")).hasCount(1);
        chooseTheFirstResultAgain();
        assertThat(page.evaluate("window.sent")).isEqualTo(1);

        // coming back to the page with the browser's Back button shows it as it was left, so it is released then
        page.evaluate("window.dispatchEvent(new PageTransitionEvent('pageshow', {persisted: true}))");

        assertThat(cursor()).isNotEqualTo("wait");
        assertThat(page.locator("#search-results button").first()).isEnabled();
        assertThat(page.locator("#search-results .loading")).hasCount(0);
    }

    @Test
    void aTriedPlanWaitsVisiblySendsOneRequestAndIsReleasedByTheAnswer() {
        page.navigate(url("/try"));
        search();
        var held = new ArrayList<Route>();
        page.route("**/try/products", held::add);

        page.locator("#search-results button").first().click();

        assertThat(page.locator("#search-results button").first()).isDisabled();
        assertThat(cursor()).isEqualTo("wait");
        chooseTheFirstResultAgain();
        page.waitForTimeout(300);
        assertThat(held).hasSize(1);

        held.getFirst().resume();

        assertThat(page.locator("#products")).containsText("Test Hauler");
        assertThat(cursor()).isNotEqualTo("wait");
        // and the next product can be added
        page.unroute("**/try/products");
        search();
        assertThat(page.locator("#search-results button").first()).isEnabled();
    }
}
