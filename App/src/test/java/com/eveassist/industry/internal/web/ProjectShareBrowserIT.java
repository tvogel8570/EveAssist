package com.eveassist.industry.internal.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.eveassist.IntegrationTest;
import com.eveassist.identity.internal.TestSignIn;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.nio.file.Files;
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

/** Copying the text of an issued quote and downloading its CSV, in a real browser, which MockMvc cannot show (the clipboard and the download). */
@Import(TestSignIn.class)
class ProjectShareBrowserIT extends IntegrationTest {

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
    void issueAQuote() {
        var sub = newSub();
        context = browser.newContext();
        context.grantPermissions(List.of("clipboard-read", "clipboard-write"));
        page = context.newPage();
        page.onPageError(pageErrors::add);
        page.navigate(url("/test-sign-in?sub=" + sub));
        page.navigate(url("/plans"));
        page.fill("#new-plan-name", "Haulers for Kestrel");
        page.click("#new-plan");
        page.waitForURL("**/plans/*");
        page.locator("#product-search").pressSequentially("hauler");
        page.locator("#search-results button", new Page.LocatorOptions().setHasText("Test Hauler")).first().click();
        page.locator("#products td", new Page.LocatorOptions().setHasText("Test Hauler")).first().waitFor();
        page.navigate(url("/projects"));
        page.click("#start-project");
        page.click("#start-project-confirm");
        page.waitForURL("**/projects/*");

        // a facility, because a costing without one cannot be quoted
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
        page.fill("#issue-form input[name=buyer]", "Kestrel Dawn");
        page.fill("#issue-form textarea[name=note]", "Delivery to Perimeter.");
        page.click("#issue-quote");
        page.waitForSelector("#quote-1");
        page.click("#quote-1 summary:has-text('Text and CSV')");
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
    void theCopyButtonPutsTheTextOfTheQuoteOnTheClipboardAndSaysSo() {
        assertThat(page.locator("#quote-1 [data-copied]").isVisible()).isFalse();

        page.click("#copy-quote-1");

        var clipboard = (String) page.evaluate("navigator.clipboard.readText()");
        assertThat(clipboard).startsWith("Quote 1 for Kestrel Dawn\n").contains("Delivery to Perimeter.\nDelivered by contract\n\nTest Hauler: 1 \u00d7 ")
                .contains(" = ").contains("\nTotal: ").endsWith(" ISK");
        assertThat(clipboard).isEqualTo(page.inputValue("#quote-text-1"));
        assertThat(page.locator("#quote-1 [data-copied]").isVisible()).isTrue();
    }

    @Test
    void theCsvLinkDownloadsAFileNamedAfterTheProjectAndTheQuote() throws Exception {
        var download = page.waitForDownload(() -> page.click("#csv-quote-1"));

        assertThat(download.suggestedFilename()).isEqualTo("quote-haulers-for-kestrel-1.csv");
        var body = Files.readString(download.path());
        assertThat(body).startsWith("Quote,Buyer,Valid until,Note\r\n1,Kestrel Dawn,").contains(",Delivery to Perimeter.\r\n\r\nItem,Quantity,Price (ISK),Extension (ISK)\r\n")
                .contains("Test Hauler,1,").contains("\r\nTotal,,,");
    }
}
