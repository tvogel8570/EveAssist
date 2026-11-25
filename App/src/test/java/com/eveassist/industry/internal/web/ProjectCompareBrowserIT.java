package com.eveassist.industry.internal.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.eveassist.IntegrationTest;
import com.eveassist.identity.internal.TestSignIn;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
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
 * Layout B of the comparison page in a real browser: what is typed beside the app's number is read and marked, kept for the costing it was typed
 * against, and started over for a new one. MockMvc cannot show it, because it is a script.
 */
@Import(TestSignIn.class)
class ProjectCompareBrowserIT extends IntegrationTest {

    private static Playwright playwright;
    private static Browser browser;

    @LocalServerPort
    int port;

    @Autowired
    JdbcClient jdbc;

    private BrowserContext context;
    private Page page;
    private String project;
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
    void costAProjectAndOpenTheComparison() {
        var sub = newSub();
        context = browser.newContext();
        page = context.newPage();
        page.onPageError(pageErrors::add);
        page.navigate(url("/test-sign-in?sub=" + sub));
        page.navigate(url("/plans"));
        page.fill("#new-plan-name", "Compared");
        page.click("#new-plan");
        page.waitForURL("**/plans/*");
        page.locator("#product-search").pressSequentially("hauler");
        page.locator("#search-results button", new Page.LocatorOptions().setHasText("Test Hauler")).first().click();
        page.locator("#products td", new Page.LocatorOptions().setHasText("Test Hauler")).first().waitFor();
        page.navigate(url("/projects"));
        page.click("#start-project");
        page.click("#start-project-confirm");
        page.waitForURL("**/projects/*");
        project = page.url();
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
        page.click("#cost-it");
        page.waitForLoadState();
        page.navigate(project + "/compare");
    }

    @AfterEach
    void close() {
        context.close();
        assertThat(pageErrors).isEmpty();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private void switchToTable() {
        page.click("#view-b");
        assertThat(page.locator("#compare").getAttribute("data-view")).isEqualTo("b");
    }

    /** A row of the first job, found by its kind (q for a quantity or an amount, dur for the duration, pct for a time line). */
    private Locator row(String kind, int position) {
        return page.locator("#job-0 tr[data-row][data-kind=" + kind + "]").nth(position);
    }

    private void type(Locator row, String text) {
        row.locator("input[data-game]").fill(text);
    }

    private String state(Locator row) {
        return row.getAttribute("data-state");
    }

    private String difference(Locator row) {
        return row.locator("[data-diff]").textContent().strip();
    }

    @Test
    void theCardsShowFirstAndTheTableReplacesThemAndWhichOneShowsIsKept() {
        assertThat(page.locator("#job-0 .view-a").isVisible()).isTrue();
        assertThat(page.locator("#job-0 .view-b").isVisible()).isFalse();

        switchToTable();
        assertThat(page.locator("#job-0 .view-b").isVisible()).isTrue();
        assertThat(page.locator("#job-0 .view-a").isVisible()).isFalse();

        page.reload();
        assertThat(page.locator("#compare").getAttribute("data-view")).isEqualTo("b");
        assertThat(page.locator("#job-0 .view-b").isVisible()).isTrue();
    }

    @Test
    void aTypedValueIsMarkedAsMatchingOrDifferingAndSaysByHowMuch() {
        switchToTable();
        var material = row("q", 0);
        var app = Long.parseLong(material.getAttribute("data-app"));
        assertThat(state(material)).isNull();

        type(material, String.format("%,d", app));
        assertThat(state(material)).isEqualTo("match");
        assertThat(difference(material)).isEqualTo("✓");

        // the app has 7 more than the game shows
        type(material, String.valueOf(app - 7));
        assertThat(state(material)).isEqualTo("differ");
        assertThat(difference(material)).isEqualTo("+7");
        type(material, String.valueOf(app + 3));
        assertThat(difference(material)).isEqualTo("-3");

        type(material, "abc");
        assertThat(state(material)).isEqualTo("differ");
        assertThat(difference(material)).isEqualTo("?");

        type(material, "");
        assertThat(state(material)).isNull();
        assertThat(difference(material)).isEmpty();
    }

    @Test
    void theGamesShortenedFormMatchesWhenTheAppsQuantityRoundsToIt() {
        switchToTable();
        var material = row("q", 0);
        // the page's rows are made by the server, so a large quantity is put on the row here to try the game's 0.31M and 92.13K
        material.evaluate("row => row.dataset.app = '314497'");

        type(material, "0.31M");
        assertThat(state(material)).isEqualTo("match");
        type(material, "0.32M");
        assertThat(state(material)).isEqualTo("differ");
        material.evaluate("row => row.dataset.app = '92130'");
        type(material, "92.13K");
        assertThat(state(material)).isEqualTo("match");
        type(material, "92.14K");
        assertThat(state(material)).isEqualTo("differ");
    }

    @Test
    void aDurationIsReadInTheGamesClockFormAndAPercentToATenth() {
        switchToTable();
        var duration = row("dur", 0);
        duration.evaluate("row => row.dataset.app = '92045'");

        type(duration, "1D 01:34:05");
        assertThat(state(duration)).isEqualTo("match");
        type(duration, "1D 01:34:06");
        assertThat(state(duration)).isEqualTo("differ");
        assertThat(difference(duration)).isEqualTo("-1");
        type(duration, "92045");
        assertThat(state(duration)).isEqualTo("match");

        var line = row("pct", 0);
        var app = Double.parseDouble(line.getAttribute("data-app"));
        type(line, String.format("%.1f%%", app));
        assertThat(state(line)).isEqualTo("match");
        type(line, String.format("%.1f%%", app - 1.5));
        assertThat(state(line)).isEqualTo("differ");
    }

    @Test
    void whatWasTypedStaysAfterAReloadAndTheSummaryCountsIt() {
        switchToTable();
        var first = row("q", 0);
        var second = row("q", 1);
        type(first, first.getAttribute("data-app"));
        type(second, String.valueOf(Long.parseLong(second.getAttribute("data-app")) + 5));
        var summary = page.locator("#job-0 [data-summary]");
        assertThat(summary.textContent()).contains("2 of ").contains("1 match").contains("1 differ");

        page.reload();

        assertThat(row("q", 0).locator("input[data-game]").inputValue()).isEqualTo(first.getAttribute("data-app"));
        assertThat(state(row("q", 0))).isEqualTo("match");
        assertThat(state(row("q", 1))).isEqualTo("differ");
        assertThat(page.locator("#job-0 [data-summary]").textContent()).contains("2 of ").contains("1 match").contains("1 differ");

        page.click("#job-0 [data-clear-job]");
        assertThat(row("q", 0).locator("input[data-game]").inputValue()).isEmpty();
        assertThat(page.locator("#job-0 [data-summary]").textContent()).contains("0 of ");
        page.reload();
        assertThat(row("q", 0).locator("input[data-game]").inputValue()).isEmpty();
    }

    @Test
    void aNewCostingStartsEmptyBecauseWhatWasTypedBelongsToTheOneItWasTypedAgainst() {
        switchToTable();
        var first = row("q", 0);
        type(first, first.getAttribute("data-app"));
        assertThat(state(first)).isEqualTo("match");

        // a changed margin and a new costing: the costing's time is its key
        page.navigate(project);
        page.fill("#quote-settings input[name=marginPercent]", "25");
        page.click("#save-quote-settings");
        page.waitForLoadState();
        page.click("#cost-it");
        page.waitForLoadState();
        page.navigate(project + "/compare");
        switchToTable();

        assertThat(row("q", 0).locator("input[data-game]").inputValue()).isEmpty();
        assertThat(state(row("q", 0))).isNull();
    }
}
