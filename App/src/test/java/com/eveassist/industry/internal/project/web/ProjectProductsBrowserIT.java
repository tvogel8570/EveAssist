package com.eveassist.industry.internal.project.web;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.eveassist.IntegrationTest;
import com.eveassist.identity.internal.TestSignIn;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Dialog;
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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.beans.factory.annotation.Autowired;

/** The Products section in a real browser: a change is saved as it is made, a removal asks first, and unsaved work elsewhere is not lost silently. */
@Import(TestSignIn.class)
class ProjectProductsBrowserIT extends IntegrationTest {

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
    void openAProjectOfOneHauler() {
        context = browser.newContext();
        page = context.newPage();
        page.onPageError(pageErrors::add);
        page.navigate(url("/test-sign-in?sub=" + newSub()));
        page.navigate(url("/plans"));
        page.fill("#new-plan-name", "Products plan");
        page.click("#new-plan");
        page.waitForURL("**/plans/*");
        page.locator("#product-search").pressSequentially("hauler");
        page.locator("#search-results button", new Page.LocatorOptions().setHasText("Test Hauler")).first().click();
        page.locator("td", new Page.LocatorOptions().setHasText("Test Hauler")).first().waitFor();
        page.waitForLoadState();
        page.navigate(url("/projects"));
        page.click("#start-project");
        page.click("#start-project-confirm");
        page.waitForURL("**/projects/*");
    }

    @AfterEach
    void close() {
        context.close();
        assertThat(pageErrors).isEmpty();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private List<Long> quantities() {
        var id = java.util.UUID.fromString(page.url().replaceAll(".*/projects/([0-9a-f-]{36}).*", "$1"));
        return jdbc.sql("SELECT x.quantity FROM project_product x JOIN project p ON p.id = x.project_id WHERE p.public_id = :id ORDER BY x.type_id")
                .param("id", id).query(Long.class).list();
    }

    private long version() {
        var id = java.util.UUID.fromString(page.url().replaceAll(".*/projects/([0-9a-f-]{36}).*", "$1"));
        return jdbc.sql("SELECT version FROM project WHERE public_id = :id").param("id", id).query(Long.class).single();
    }

    /** Waits for the page the change leads back to. */
    private void saving(Runnable change) {
        page.waitForNavigation(change);
        page.waitForLoadState();
    }

    @Test
    void theSectionIsOpenAndAQuantityIsSavedAsItIsLeftWithEnterSavingItOnce() {
        assertThat(page.locator("#products")).hasAttribute("open", "");

        saving(() -> {
            page.fill("#quantity-1001", "3");
            page.press("#quantity-1001", "Tab");
        });

        assertThat(quantities()).containsExactly(3L);
        assertThat(page.locator("#banner-stale")).isVisible();
        var before = version();

        saving(() -> {
            page.fill("#quantity-1001", "4");
            page.press("#quantity-1001", "Enter");
        });

        assertThat(quantities()).containsExactly(4L);
        assertThat(version()).isEqualTo(before + 1);
        assertThat(page.locator("#error")).hasCount(0);
    }

    @Test
    void aPartIsSwitchedAndAProductAddedFromTheSearchAndRemovedAfterAsking() {
        saving(() -> page.click("#buy-1002"));
        assertThat(page.locator("#buy-1002")).hasAttribute("aria-pressed", "true");

        page.locator("#product-search").pressSequentially("second");
        saving(() -> page.locator("#search-results button").first().click());
        assertThat(quantities()).containsExactly(1L, 1L);

        page.click("#remove-1003");
        assertThat(quantities()).containsExactly(1L, 1L);
        saving(() -> page.click("#remove-confirm-1003"));
        assertThat(quantities()).containsExactly(1L);
    }

    @Test
    void aChangeThatWouldLoseUnsavedWorkElsewhereAsksFirst() {
        page.click("#settings > summary");
        page.fill("#settings-form input[name=contractFee]", "12345");
        var asked = new ArrayList<String>();
        page.onceDialog(dialog -> {
            asked.add(dialog.message());
            dialog.dismiss();
        });

        page.fill("#quantity-1001", "9");
        page.press("#quantity-1001", "Tab");

        page.waitForTimeout(500);
        assertThat(asked).singleElement().asString().contains("unsaved");
        assertThat(quantities()).containsExactly(1L);
        assertThat(page.locator("#quantity-1001")).hasValue("1");
        assertThat(page.locator("#settings-form input[name=contractFee]")).hasValue("12345");

        page.onceDialog(Dialog::accept);
        saving(() -> {
            page.fill("#quantity-1001", "9");
            page.press("#quantity-1001", "Tab");
        });

        assertThat(quantities()).containsExactly(9L);
    }
}
