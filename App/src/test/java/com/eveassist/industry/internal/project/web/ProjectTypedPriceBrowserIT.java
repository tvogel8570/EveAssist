package com.eveassist.industry.internal.project.web;

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
 * Typing a price beside a warning in a real browser: the unsaved mark, saving, costing again to a quote, and taking the market price back. The
 * project is market-referenced and its product has no Jita price in the test data, so it cannot be quoted until a price is typed.
 */
@Import(TestSignIn.class)
class ProjectTypedPriceBrowserIT extends IntegrationTest {

    private static Playwright playwright;
    private static Browser browser;

    @LocalServerPort
    int port;

    @Autowired
    JdbcClient jdbc;

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
    void aMarketReferencedProjectWithAProductThatHasNoPrice() {
        var sub = newSub();
        context = browser.newContext();
        page = context.newPage();
        page.onPageError(pageErrors::add);
        page.navigate(url("/test-sign-in?sub=" + sub));
        page.navigate(url("/plans"));
        page.fill("#new-plan-name", "Typed prices");
        page.click("#new-plan");
        page.waitForURL("**/plans/*");
        page.locator("#product-search").pressSequentially("hauler");
        page.locator("#search-results button", new Page.LocatorOptions().setHasText("Test Hauler")).first().click();
        page.locator("#products td", new Page.LocatorOptions().setHasText("Test Hauler")).first().waitFor();
        page.navigate(url("/projects"));
        page.click("#start-project");
        page.click("#start-project-confirm");
        page.waitForURL("**/projects/*");
        jdbc.sql("""
                INSERT INTO facility (public_id, owner_account_id, name, structure_material_percent, structure_time_percent,
                    structure_cost_percent, system_cost_index_percent, scc_surcharge_percent)
                VALUES (:id, :owner, 'Test Azbel', 1, 20, 4, 2, 4)
                """).param("id", UUID.randomUUID()).param("owner", viewerFor(sub).accountId().value()).update();
        page.reload();
        page.click("#settings > summary");
        page.selectOption("#default-facility", new com.microsoft.playwright.options.SelectOption().setLabel("Test Azbel"));
        page.click("#save-settings");
        page.waitForLoadState();
        page.check("#quote-settings input[value=MARKET]");
        page.click("#save-quote-settings");
        page.waitForLoadState();
        page.click("#cost-it");
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

    private String box() {
        return "#warnings form input[name=price]";
    }

    @Test
    void theBoxBesideTheWarningSaysItHasUnsavedChangesAndSoDoesTheSectionHeader() {
        assertThat(page.locator("#warnings").textContent()).contains("Test Hauler has no Jita price");
        assertThat(page.locator("#warnings [data-unsaved-note]").first().isVisible()).isFalse();

        page.fill(box(), "1,250,000");

        assertThat(page.locator("#warnings [data-unsaved-note]").first().isVisible()).isTrue();
        page.click("#warnings > summary");
        assertThat(page.locator("#warnings > summary [data-section-unsaved]").isVisible()).isTrue();
    }

    @Test
    void aTypedPriceMakesTheQuoteAfterCostingAgainAndCanBeGivenUp() {
        assertThat(page.locator("#quote-price").count()).isZero();

        page.fill(box(), "1,250,000");
        page.click("#warnings button[id^=save-price-]");
        page.waitForURL("**#warnings");

        assertThat(page.locator("#warnings").textContent()).contains("Typed: 1,250,000.00 ISK per unit. Press Cost it again.");
        page.click("#cost-it");
        page.waitForLoadState();
        assertThat(page.locator("#quote-price").textContent()).contains("1,250,000 ISK");
        // the warning that blocked the quote is gone. The notices about adjusted prices the test data has none of stay, and block nothing
        assertThat(page.locator("#warnings").textContent()).doesNotContain("has no Jita price");
        assertThat(page.locator("#cannot-issue").count()).isZero();

        page.click("#typed-prices > summary");
        assertThat(page.locator("#typed-prices").textContent()).contains("Test Hauler").contains("1,250,000.00");
        page.click("#typed-prices button[id^=use-market-]");
        page.waitForLoadState();

        assertThat(page.locator("#typed-prices").count()).isZero();
        // the costing is out of date, because an input changed
        assertThat(page.locator("#banner-stale").isVisible()).isTrue();
    }
}
