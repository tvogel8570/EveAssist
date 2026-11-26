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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/**
 * The actions that can wait on ESI, besides adding a product: costing a project (also as it is started), taking a market price again, and
 * refreshing the contracts. Each shows a wait cursor, disables its button and sends once. The page is kept where it is, so that it can be
 * looked at while the action would be under way.
 */
@Import(TestSignIn.class)
class SlowActionsBrowserIT extends IntegrationTest {

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
    void signInWithAPlan() {
        context = browser.newContext();
        page = context.newPage();
        page.onPageError(pageErrors::add);
        page.navigate(url("/test-sign-in?sub=" + newSub()));
        page.navigate(url("/plans"));
        page.fill("#new-plan-name", "Slow plan");
        page.click("#new-plan");
        page.waitForURL("**/plans/*");
        page.locator("#product-search").pressSequentially("hauler");
        page.locator("#search-results button", new Page.LocatorOptions().setHasText("Test Hauler")).first().click();
        page.locator("td", new Page.LocatorOptions().setHasText("Test Hauler")).first().waitFor();
        page.waitForLoadState();
    }

    @AfterEach
    void close() {
        context.close();
        assertThat(pageErrors).isEmpty();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private void startAProject() {
        page.navigate(url("/projects"));
        page.click("#start-project");
        page.click("#start-project-confirm");
        page.waitForURL("**/projects/*");
    }

    /** Clicks the button with the page held, checks that it waits visibly and sends once, and releases the page again. */
    private void waitsVisibly(String button) {
        page.evaluate("window.sent = 0; window.hold = event => { window.sent++; event.preventDefault(); }; window.addEventListener('submit', window.hold)");

        page.locator(button).first().click();

        assertThat(page.locator(button).first()).isDisabled();
        assertThat(page.evaluate("getComputedStyle(document.body).cursor")).isEqualTo("wait");
        page.evaluate("selector => document.querySelector(selector).form.requestSubmit()", button);
        assertThat(page.evaluate("window.sent")).isEqualTo(1);

        page.evaluate("window.removeEventListener('submit', window.hold); window.dispatchEvent(new PageTransitionEvent('pageshow', {persisted: true}))");
        assertThat(page.locator(button).first()).isEnabled();
    }

    @Test
    void startingAProjectAndRefreshingTheContractsWaitVisibly() {
        page.navigate(url("/projects"));
        page.click("#start-project");
        waitsVisibly("#start-project-confirm");

        page.navigate(url("/market/blueprint-copies"));
        waitsVisibly("#refresh-contracts");
    }

    @Test
    void everyWayToCostAndTakingTheMarketPriceAgainWaitVisibly() {
        startAProject();
        waitsVisibly("#cost-it-current");

        // a price to give up: the part is bought, which the test data has no market price for, and one is typed
        page.waitForNavigation(() -> page.click("#buy-1002"));
        // the change made the costing out of date, so the banner offers to cost it
        waitsVisibly("#cost-it");
        page.waitForNavigation(() -> page.click("#cost-it"));
        page.fill("#warnings input[name=price]", "40");
        page.waitForNavigation(() -> page.click("#warnings button[id^=save-price-]"));
        page.click("#typed-prices > summary");
        waitsVisibly("#use-market-1002");
    }
}
