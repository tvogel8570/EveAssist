package com.eveassist.industry.internal.web;

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
 * The "unsaved changes" mark on the project page in a real browser. A choice made in a form is lost if Cost it again is pressed before
 * that form's Save, so each form says so as soon as one of its fields is edited. MockMvc cannot show it: it is a script and styles.
 */
@Import(TestSignIn.class)
class ProjectUnsavedBrowserIT extends IntegrationTest {

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
    void signInAndOpenAProject() {
        context = browser.newContext();
        page = context.newPage();
        page.onPageError(pageErrors::add);
        page.navigate(url("/test-sign-in?sub=" + newSub()));
        page.navigate(url("/plans"));
        page.fill("#new-plan-name", "Browser plan");
        page.click("#new-plan");
        page.waitForURL("**/plans/*");
        page.locator("#product-search").pressSequentially("hauler");
        page.locator("#search-results button", new Page.LocatorOptions().setHasText("Test Hauler")).click();
        page.locator("#products td", new Page.LocatorOptions().setHasText("Test Hauler")).waitFor();
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

    private boolean noteShown(String form) {
        return page.locator(form + " [data-unsaved-note]").first().isVisible();
    }

    private String saveBorder(String button) {
        return (String) page.locator(button).evaluate("button => getComputedStyle(button).borderColor");
    }

    @Test
    void aFormSaysItHasUnsavedChangesAsSoonAsAFieldInItIsEdited() {
        assertThat(noteShown("#settings-form")).isFalse();
        var calm = saveBorder("#save-settings");

        page.fill("#settings-form input[name=contractFee]", "20000");

        assertThat(noteShown("#settings-form")).isTrue();
        assertThat(saveBorder("#save-settings")).isNotEqualTo(calm);
        // another form on the page is not marked by it
        assertThat(noteShown("#quote-settings")).isFalse();
    }

    @Test
    void aDropdownCountsAsAnEditToo() {
        page.locator("#settings-form select[name=priceSide]").selectOption("BUY");

        assertThat(noteShown("#settings-form")).isTrue();
    }

    @Test
    void anItemRowIsMarkedByItsOwnFieldsWhichSitOutsideItsFormTags() {
        assertThat(noteShown("#item-form-1001")).isFalse();

        page.fill("tr#item-1001 input[name=me]", "8");

        assertThat(noteShown("#item-form-1001")).isTrue();
        assertThat(noteShown("#settings-form")).isFalse();
    }

    @Test
    void savingReloadsThePageWithTheMarkGone() {
        page.fill("#quote-settings input[name=marginPercent]", "25");
        assertThat(noteShown("#quote-settings")).isTrue();

        page.click("#save-quote-settings");
        page.waitForLoadState();

        assertThat(noteShown("#quote-settings")).isFalse();
        assertThat(page.inputValue("#quote-settings input[name=marginPercent]")).isEqualTo("25");
    }
}
