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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/**
 * The project page's sections in a real browser: closed by the user and remembered in this browser, opened by themselves when they hold
 * something the user must see, marked in their header when they hold unsaved changes, and a save that returns to the part it was made in.
 */
@Import(TestSignIn.class)
class ProjectSectionsBrowserIT extends IntegrationTest {

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
    void openAProject() {
        context = browser.newContext();
        page = context.newPage();
        page.onPageError(pageErrors::add);
        page.navigate(url("/test-sign-in?sub=" + newSub()));
        page.navigate(url("/plans"));
        page.fill("#new-plan-name", "Sections plan");
        page.click("#new-plan");
        page.waitForURL("**/plans/*");
        addProduct("hauler", "Test Hauler");
        addProduct("nested", "Nested Ship");
        addProduct("second", "Second");
        startProject();
    }

    @AfterEach
    void close() {
        context.close();
        assertThat(pageErrors).isEmpty();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private void addProduct(String search, String name) {
        page.locator("#product-search").fill("");
        page.locator("#product-search").pressSequentially(search);
        page.locator("#search-results button", new Page.LocatorOptions().setHasText(name)).first().click();
        page.locator("#products td", new Page.LocatorOptions().setHasText(name)).first().waitFor();
        page.waitForLoadState();
    }

    private void startProject() {
        page.navigate(url("/projects"));
        page.click("#start-project");
        page.click("#start-project-confirm");
        page.waitForURL("**/projects/*");
    }

    private boolean isOpen(String section) {
        return (Boolean) page.locator("#" + section).evaluate("details => details.open");
    }

    private void keptAs(String section, String state) {
        page.waitForFunction("([key, value]) => localStorage.getItem(key) === value", new Object[] {"eveassist.section." + section, state});
    }

    private void open(String section) {
        if (!isOpen(section)) {
            page.click("#" + section + " > summary");
        }
    }

    private String chevron(String section) {
        return (String) page.locator("#" + section + " > summary svg").evaluate("svg => getComputedStyle(svg).rotate");
    }

    /** One triangle everywhere: in a circle, in the theme's accent colour, and turned when what it stands for is open. */
    @Test
    void everySectionHasTheSharedTriangleInAnAccentCircleThatTurnsWhenItOpens() {
        for (var section : List.of("settings", "items", "warnings", "jobs", "delivered-cost")) {
            var triangle = page.locator("#" + section + " > summary > .chevron");
            assertThat(triangle.count()).as(section).isEqualTo(1);
            var look = (String) triangle.evaluate("""
                    mark => {
                        const probe = document.body.appendChild(document.createElement('span'));
                        probe.style.color = 'var(--color-accent)';
                        const accent = getComputedStyle(probe).color;
                        probe.remove();
                        const style = getComputedStyle(mark);
                        return [style.color === accent, style.width === style.height, parseFloat(style.borderTopLeftRadius) >= parseFloat(style.width) / 2,
                                style.backgroundColor !== 'rgba(0, 0, 0, 0)'].join(' ');
                    }""");
            assertThat(look).as(section).isEqualTo("true true true true");
        }
        assertThat(chevron("jobs")).isEqualTo("none");
        page.click("#jobs > summary");
        page.waitForFunction("getComputedStyle(document.querySelector('#jobs > summary .chevron svg')).rotate === '90deg'");
    }

    /** The space under what an open section holds, and for the warnings the room their bullets need left of the text, in pixels. */
    private String roomIn(String section) {
        return (String) page.locator("#" + section).evaluate("""
                details => {
                    const box = details.getBoundingClientRect();
                    const shown = [...details.querySelectorAll(':scope > :not(summary) *, :scope > :not(summary)')]
                            .map(e => e.getBoundingClientRect()).filter(r => r.height > 0);
                    const below = Math.round(box.bottom - Math.max(...shown.map(r => r.bottom)));
                    const item = details.querySelector('li');
                    return below + ' ' + (item ? Math.round(item.getBoundingClientRect().left - box.left) : '-');
                }""");
    }

    @Test
    void anOpenSectionHasRoomUnderWhatItHoldsAndTheWarningsBulletsSitInsideIt() {
        for (var section : List.of("settings", "products", "items", "warnings", "jobs", "delivered-cost")) {
            open(section);
            var room = roomIn(section).split(" ");
            assertThat(Integer.parseInt(room[0])).as(section + " below, " + roomIn(section)).isBetween(14, 20);
            if (section.equals("warnings")) {
                // the text of a bulleted line starts where a bullet fits between it and the box: the section's 16 pixels and the list's 20
                assertThat(Integer.parseInt(room[1])).as("warnings text from the left").isBetween(34, 40);
            }
        }
    }

    @Test
    void sectionsStartClosedExceptTheOneWithWarnings() {
        for (var section : List.of("settings", "items", "jobs", "delivered-cost")) {
            assertThat(isOpen(section)).as(section).isFalse();
        }
        // no facility is chosen, so the costing warns, and the warnings are the one section that has something in it
        assertThat(isOpen("warnings")).isTrue();
    }

    @Test
    void aSectionTheUserOpenedStaysOpenAfterAReloadAndOneTheUserClosedStaysClosed() {
        page.click("#jobs > summary");
        assertThat(isOpen("jobs")).isTrue();
        keptAs("jobs", "open");

        page.reload();
        assertThat(isOpen("jobs")).isTrue();
        // the others were not touched
        assertThat(isOpen("items")).isFalse();

        page.click("#jobs > summary");
        keptAs("jobs", "closed");
        page.reload();
        assertThat(isOpen("jobs")).isFalse();
    }

    @Test
    void whatWasKeptIsTheSameForEveryProject() {
        page.click("#jobs > summary");
        keptAs("jobs", "open");

        startProject();

        assertThat(isOpen("jobs")).isTrue();
    }

    @Test
    void aWarningThatBlocksTheQuoteOpensItsSectionWhateverWasKept() {
        assertThat(isOpen("warnings")).isTrue();
        page.evaluate("localStorage.setItem('eveassist.section.warnings', 'closed')");

        page.reload();

        assertThat(isOpen("warnings")).isTrue();
    }

    @Test
    void theHeaderShowsWhetherTheSectionIsOpenWithAChevronThatTurns() {
        // closed: the chevron points right and is not turned. Open: it is turned a quarter, to point down (the transition takes a moment).
        // Tailwind 4 turns an element with the rotate property and not with transform.
        assertThat(chevron("items")).isEqualTo("none");

        page.click("#items > summary");
        page.waitForFunction("() => getComputedStyle(document.querySelector('#items > summary svg')).rotate === '90deg'");
        assertThat(isOpen("items")).isTrue();

        page.click("#items > summary");
        page.waitForFunction("() => getComputedStyle(document.querySelector('#items > summary svg')).rotate === 'none'");
        assertThat(isOpen("items")).isFalse();
    }

    @Test
    void aClosedSectionStillShowsThatItHasUnsavedChanges() {
        open("settings");
        assertThat(page.locator("#settings [data-section-unsaved]").isVisible()).isFalse();

        page.fill("#settings-form input[name=contractFee]", "20000");
        page.click("#settings > summary");

        assertThat(isOpen("settings")).isFalse();
        assertThat(page.locator("#settings > summary [data-section-unsaved]").isVisible()).isTrue();
        // the item rows are in another section, which is not marked by it
        assertThat(page.locator("#items > summary [data-section-unsaved]").isVisible()).isFalse();
    }

    @Test
    void anItemRowThatIsEditedMarksItsSectionToo() {
        open("items");
        page.fill("tr[id^=item-] input[name=me]", "8");
        page.click("#items > summary");

        assertThat(page.locator("#items > summary [data-section-unsaved]").isVisible()).isTrue();
    }

    @Test
    void aRefusedChangeOpensTheSectionsThatTakeInputWhateverWasKept() {
        open("items");
        // an ME of 11 is refused, and the browser would stop the form first, so its checks are switched off
        page.evaluate("document.querySelectorAll('form').forEach(form => form.noValidate = true)");
        page.fill("tr[id^=item-] input[name=me]", "11");
        // both sections were kept closed, as far as the page is told, when the page comes back
        page.evaluate("localStorage.setItem('eveassist.section.items', 'closed'); localStorage.setItem('eveassist.section.settings', 'closed')");
        page.click("#items button[id^=save-item-] >> nth=0");
        page.waitForSelector("#error");

        assertThat(isOpen("items")).isTrue();
        assertThat(isOpen("settings")).isTrue();
    }

    @Test
    void savingAnItemReturnsToItsRowAndSavingSettingsToTheSettings() {
        page.setViewportSize(1280, 500);
        open("items");
        var last = page.locator("tr[id^=item-]").last();
        var rowId = last.getAttribute("id");
        last.locator("input[name=me]").fill("8");
        last.locator("button[id^=save-item-]").click();
        page.waitForURL("**#" + rowId);

        assertThat((Boolean) page.evaluate("window.scrollY > 0")).isTrue();
        assertThat((Boolean) page.locator("#" + rowId).evaluate(
                "row => row.getBoundingClientRect().top >= 0 && row.getBoundingClientRect().top < window.innerHeight")).isTrue();

        open("settings");
        page.fill("#settings-form input[name=contractFee]", "20000");
        page.click("#save-settings");
        page.waitForURL("**#settings");
        assertThat(page.url()).endsWith("#settings");
    }
}
