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

/** The facility forms in a real browser: the structure shortcut, adding from a project's page, and the delete that asks first. */
@Import(TestSignIn.class)
class FacilityBrowserIT extends IntegrationTest {

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
    void signIn() {
        context = browser.newContext();
        page = context.newPage();
        page.onPageError(pageErrors::add);
        page.navigate(url("/test-sign-in?sub=" + newSub()));
    }

    @AfterEach
    void close() {
        context.close();
        assertThat(pageErrors).isEmpty();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private String value(String form, String field) {
        return page.inputValue(form + " input[name=" + field + "]");
    }

    private void addFacility(String form, String name, String structure, String index) {
        page.fill(form + " input[name=name]", name);
        if (structure != null) {
            page.selectOption(form + " select[name=structureType]", structure);
        }
        page.fill(form + " input[name=systemCostIndexPercent]", index);
        page.click(form + " button[type=submit]");
    }

    @Test
    void fillProposesTheChosenStructuresBonusesAndMarksTheFormUnsaved() {
        page.navigate(url("/facilities"));
        assertThat(page.locator("#add-form [data-unsaved-note]").isVisible()).isFalse();

        page.selectOption("#add-form select[name=structureType]", "azbel");
        page.click("#add-form [data-fill-structure]");
        assertThat(value("#add-form", "structureMaterialPercent")).isEqualTo("1");
        assertThat(value("#add-form", "structureTimePercent")).isEqualTo("20");
        assertThat(value("#add-form", "structureCostPercent")).isEqualTo("4");
        assertThat(page.locator("#add-form [data-unsaved-note]").isVisible()).isTrue();

        page.selectOption("#add-form select[name=structureType]", "raitaru");
        page.click("#add-form [data-fill-structure]");
        assertThat(value("#add-form", "structureTimePercent")).isEqualTo("15");
        assertThat(value("#add-form", "structureCostPercent")).isEqualTo("3");

        // "Not said" proposes nothing, so what is typed stays
        page.selectOption("#add-form select[name=structureType]", "");
        page.click("#add-form [data-fill-structure]");
        assertThat(value("#add-form", "structureTimePercent")).isEqualTo("15");
    }

    @Test
    void aProposedValueCanBeTypedOverAndTheFacilityIsSavedWithWhatWasTyped() {
        page.navigate(url("/facilities"));
        page.selectOption("#add-form select[name=structureType]", "azbel");
        page.click("#add-form [data-fill-structure]");
        page.fill("#add-form input[name=structureTimePercent]", "18");
        addFacility("#add-form", "Dodenvale Azbel", null, "2.16");
        page.waitForURL("**/facilities");

        assertThat(page.locator("#facility-list").textContent()).contains("Dodenvale Azbel").contains("Cost index 2.16%").contains("typed today");
        page.click("#facility-list > details > summary");
        assertThat(page.inputValue("#facility-list input[name=structureTimePercent]")).isEqualTo("18");
        assertThat(page.inputValue("#facility-list select[name=structureType]")).isEqualTo("azbel");
    }

    @Test
    void aFacilityAddedOnAProjectsPageComesBackToItsFacilitiesSection() {
        page.navigate(url("/plans"));
        page.fill("#new-plan-name", "Plan");
        page.click("#new-plan");
        page.waitForURL("**/plans/*");
        page.locator("#product-search").pressSequentially("hauler");
        page.locator("#search-results button", new Page.LocatorOptions().setHasText("Test Hauler")).first().click();
        page.locator("#products td", new Page.LocatorOptions().setHasText("Test Hauler")).first().waitFor();
        page.navigate(url("/projects"));
        page.click("#start-project");
        page.click("#start-project-confirm");
        page.waitForURL("**/projects/*");

        page.click("#facilities > summary");
        page.click("#add-facility > summary");
        addFacility("#add-facility", "Quick Azbel", "azbel", "2.16");
        page.waitForURL("**#facilities");

        // the section was opened by the user, so it is still open, with the new facility in it and in the default facility's dropdown
        assertThat(page.locator("#facilities table").textContent()).contains("Quick Azbel").contains("typed today");
        assertThat(page.locator("#default-facility option", new Page.LocatorOptions().setHasText("Quick Azbel")).count()).isEqualTo(1);
    }

    @Test
    void deletingAsksFirstAndThenRemovesTheFacility() {
        page.navigate(url("/facilities"));
        addFacility("#add-form", "Doomed", null, "1");
        page.waitForURL("**/facilities");
        assertThat(page.locator("#facility-list").textContent()).contains("Doomed");
        page.click("#facility-list > details > summary");

        page.click("[id^=delete-]:not([id^=delete-confirm])");
        assertThat(page.locator("[id^=delete-confirm-]").isVisible()).isTrue();
        assertThat(page.locator("#facility-list").textContent()).contains("0 draft projects use it");
        page.click("[id^=delete-confirm-]");
        page.waitForURL("**/facilities");

        assertThat(page.locator("#no-facilities").isVisible()).isTrue();
        assertThat(page.locator("#deleted").textContent()).contains("Facility deleted");
    }
}
