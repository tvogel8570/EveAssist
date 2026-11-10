package com.eveassist.industry.internal.web;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.eveassist.IntegrationTest;
import com.eveassist.identity.internal.TestSignIn;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Request;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The htmx update in a real browser, which unit tests and MockMvc cannot show. */
@Import(TestSignIn.class)
class PlanEditorBrowserIT extends IntegrationTest {

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

    private void createPlanWithHauler() {
        page.fill("#new-plan-name", "Browser plan");
        page.click("#new-plan");
        page.waitForURL("**/plans/*");
        addProduct("hauler", "Test Hauler");
    }

    private void addProduct(String search, String name) {
        page.locator("#product-search").pressSequentially(search);
        page.locator("#search-results button", new Page.LocatorOptions().setHasText(name)).click();
        page.locator("#products td", new Page.LocatorOptions().setHasText(name)).waitFor();
        page.waitForLoadState();
    }

    private void markPage() {
        page.evaluate("window.notReloaded = true");
    }

    private void assertNotReloaded() {
        assertThat(page.evaluate("window.notReloaded")).isEqualTo(true);
    }

    @Test
    void anEditUpdatesChangedRowsAndTotalWithoutReloading() {
        createPlanWithHauler();
        assertThat(page.locator("#plan-total-value")).hasText("700 ISK");
        markPage();

        page.fill("#quantity-1001", "2");
        page.press("#quantity-1001", "Tab");

        assertThat(page.locator("#plan-total-value")).hasText("1,400 ISK");
        assertThat(page.locator("#line-34 td").nth(2)).hasText("240");
        assertThat(page.locator("#line-1002 td").nth(2)).hasText("4");
        assertThat(page.locator("#saved-1001")).containsText("Saved");
        assertThat(page.locator("#material-lines > #line-34")).hasCount(1);
        assertThat(page.locator("table table")).hasCount(0);
        assertNotReloaded();
    }

    @Test
    void refreshingPricesUpdatesTheTotalWithoutReloadingAndLeavesAnotherPlanAlone() {
        createPlanWithHauler();
        var planUrl = page.url();
        assertThat(page.locator("#plan-total-value")).hasText("700 ISK");
        markPage();
        esi.sell(34, "6.00");

        page.click("#refresh-prices");

        assertThat(page.locator("#plan-total-value")).hasText("820 ISK");
        assertThat(page.locator("#refresh-prices")).isEnabled();
        assertThat(page.locator("#price-warning .alert")).hasCount(0);
        assertNotReloaded();

        page.navigate(url("/plans"));
        page.fill("#new-plan-name", "Second plan");
        page.click("#new-plan");
        page.waitForURL("**/plans/*");
        addProduct("hauler", "Test Hauler");
        esi.sell(34, "7.00");
        page.navigate(planUrl);
        assertThat(page.locator("#plan-total-value")).hasText("820 ISK");
    }

    @Test
    void refreshingWhenEsiCannotAnswerForAnItemKeepsItsPriceAndWarns() {
        createPlanWithHauler();
        esi.sell(34, "6.00");
        esi.fail(35);

        page.click("#refresh-prices");

        assertThat(page.locator("#plan-total-value")).hasText("820 ISK");
        assertThat(page.locator("#price-warning .alert")).containsText("Could not refresh");
        assertThat(page.locator("#price-warning .alert")).containsText("Pilot's Mineral");

        esi.reset();
        page.click("#refresh-prices");
        assertThat(page.locator("#price-warning .alert")).hasCount(0);
    }

    @Test
    void enterSavesOnce() {
        createPlanWithHauler();
        var posts = new ArrayList<Request>();
        page.onRequest(request -> {
            if (request.method().equals("POST")) {
                posts.add(request);
            }
        });

        page.fill("#quantity-1001", "3");
        page.press("#quantity-1001", "Enter");

        assertThat(page.locator("#plan-total-value")).hasText("2,100 ISK");
        assertThat(posts).hasSize(1);
    }

    @Test
    void anUnchangedRowIsNotRedrawn() {
        createPlanWithHauler();
        addProduct("second", "Second");
        page.evaluate("document.getElementById('line-36').dataset.original = 'yes'");
        var before = page.locator("#plan-total-value").textContent();

        page.fill("#quantity-1001", "2");
        page.press("#quantity-1001", "Tab");

        assertThat(page.locator("#plan-total-value")).not().hasText(before);
        assertThat(page.locator("#line-36")).hasAttribute("data-original", "yes");
    }

    @Test
    void repeatedEditsUseTheNewVersionAndFocusStays() {
        createPlanWithHauler();

        for (int quantity = 2; quantity <= 4; quantity++) {
            page.fill("#quantity-1001", String.valueOf(quantity));
            page.press("#quantity-1001", "Enter");
            assertThat(page.locator("#plan-total-value")).hasText(String.format("%,d ISK", quantity * 700));
            assertThat(page.locator("#quantity-1001")).isFocused();
        }
        assertThat(page.locator("#error-toast")).isHidden();
    }

    @ParameterizedTest
    @ValueSource(ints = {50, 150})
    void quickEditsToTwoProductsBothSaveOnASlowConnection(int latencyMs) {
        createPlanWithHauler();
        addProduct("ammo", "Test Ammo");
        var network = context.newCDPSession(page);
        network.send("Network.enable");
        network.send("Network.emulateNetworkConditions", new com.google.gson.Gson().toJsonTree(Map.of(
                "offline", false, "latency", latencyMs, "downloadThroughput", -1, "uploadThroughput", -1))
                .getAsJsonObject());

        page.fill("#quantity-1001", "2");
        page.press("#quantity-1001", "Tab");
        page.fill("#quantity-1004", "200");
        page.press("#quantity-1004", "Tab");

        assertThat(page.locator("#line-34 td").nth(2)).hasText("242");
        assertThat(page.locator("#error-toast")).isHidden();
        page.reload();
        assertThat(page.locator("#quantity-1001")).hasValue("2");
        assertThat(page.locator("#quantity-1004")).hasValue("200");
    }

    @Test
    void aBadQuantityShowsAMessageAndKeepsThePage() {
        createPlanWithHauler();
        markPage();

        page.fill("#quantity-1001", "0");
        page.press("#quantity-1001", "Tab");

        // the reason is given, the number the plan holds returns, and nothing was sent (see also
        // unitsTheBoxCannotHoldAreRefusedWithAReasonAndTheOldNumberReturns)
        assertThat(page.locator("#error-toast")).containsText("at least 1");
        assertThat(page.locator("#quantity-1001")).hasValue("1");
        assertThat(page.locator("#plan-total-value")).hasText("700 ISK");
        assertNotReloaded();
    }

    @Test
    void anEditFromAnOutOfDatePageShowsAConflict() {
        createPlanWithHauler();
        var other = context.newPage();
        other.navigate(page.url());
        other.fill("#quantity-1001", "5");
        other.press("#quantity-1001", "Tab");
        assertThat(other.locator("#plan-total-value")).hasText("3,500 ISK");

        page.fill("#quantity-1001", "2");
        page.press("#quantity-1001", "Tab");

        assertThat(page.locator("#error-toast")).containsText("changed elsewhere");
        assertThat(page.locator("#plan-total-value")).hasText("700 ISK");
    }

    @Test
    void anEndedSessionGoesToSignInRatherThanSwappingItIn() {
        createPlanWithHauler();
        jdbc.sql("DELETE FROM spring_session").update();

        var signIn = page.waitForRequest("**/oauth2/authorization/keycloak", () -> {
            page.fill("#quantity-1001", "2");
            page.press("#quantity-1001", "Tab");
        });

        assertThat(signIn.url()).endsWith("/oauth2/authorization/keycloak");
    }

    @Test
    void buyingAPartThenBuildingItAgainChangesTheMaterialsAndTotal() {
        esi.sell(COMPONENT.value(), "100.00");
        createPlanWithHauler();
        markPage();

        page.click("#buy-1002");

        assertThat(page.locator("#plan-total-value")).hasText("700 ISK");
        assertThat(page.locator("#line-35")).hasCount(0);
        assertThat(page.locator("#line-34 td").nth(2)).hasText("100");
        assertThat(page.locator("#buy-1002")).hasAttribute("aria-pressed", "true");

        page.click("#build-1002");

        assertThat(page.locator("#line-35")).hasCount(1);
        assertThat(page.locator("#line-34 td").nth(2)).hasText("120");
        assertNotReloaded();

        page.click("#buy-1002");
        assertThat(page.locator("#buy-1002")).hasAttribute("aria-pressed", "true");
        page.reload();
        assertThat(page.locator("#buy-1002")).hasAttribute("aria-pressed", "true");
        assertThat(page.locator("#line-35")).hasCount(0);
    }

    @Test
    void theMaterialListComesAfterTheParts() {
        createPlanWithHauler();

        assertThat(page.evaluate("document.getElementById('parts-section').compareDocumentPosition("
                + "document.getElementById('materials-section')) === Node.DOCUMENT_POSITION_FOLLOWING")).isEqualTo(true);
    }

    @Test
    void arrowKeysMoveThroughSearchResultsAndEnterPicks() {
        page.fill("#new-plan-name", "Browser plan");
        page.click("#new-plan");
        page.waitForURL("**/plans/*");
        page.locator("#product-search").pressSequentially("test");
        page.locator("#search-list button").first().waitFor();
        assertThat(page.locator("#search-list button")).hasCount(3);

        page.press("#product-search", "ArrowDown");
        assertThat(page.locator("#search-list button").nth(0)).isFocused();
        page.keyboard().press("ArrowDown");
        assertThat(page.locator("#search-list button").nth(1)).isFocused();
        page.keyboard().press("ArrowUp");
        page.keyboard().press("ArrowUp");
        assertThat(page.locator("#product-search")).isFocused();
        page.keyboard().press("ArrowDown");
        page.keyboard().press("ArrowDown");
        var chosen = page.locator("#search-list button").nth(1).textContent();
        page.keyboard().press("Enter");

        page.locator("#products td", new Page.LocatorOptions().setHasText(chosen)).waitFor();
    }

    @Test
    void columnsSortBothWaysAndStaySortedAfterAnEdit() {
        createPlanWithHauler();
        addProduct("second", "Second");
        page.fill("#quantity-1003", "1");
        var heading = page.locator("#materials-section th").nth(2).locator("button");

        heading.click();
        assertThat(page.locator("#materials-section th").nth(2)).hasAttribute("aria-sort", "descending");
        assertThat(page.locator("#material-lines tr").first()).hasId("line-34");

        heading.click();
        assertThat(page.locator("#materials-section th").nth(2)).hasAttribute("aria-sort", "ascending");
        assertThat(page.locator("#material-lines tr").first()).hasId("line-36");

        page.fill("#quantity-1001", "2");
        page.press("#quantity-1001", "Tab");

        assertThat(page.locator("#plan-total-value")).hasText("1,500 ISK");
        assertThat(page.locator("#material-lines tr").first()).hasId("line-36");
        assertThat(page.locator("#material-lines tr").last()).hasId("line-34");

        page.locator("#materials-section th").nth(4).locator("button").click();
        assertThat(page.locator("#material-lines tr").last()).hasId("line-36");
    }

    @Test
    void searchWithNoMatchSaysSo() {
        page.fill("#new-plan-name", "Browser plan");
        page.click("#new-plan");
        page.waitForURL("**/plans/*");

        page.locator("#product-search").pressSequentially("zzz");

        assertThat(page.locator("#search-results")).containsText("No items found");
    }

    @Test
    void theNewButtonOnAPlanPageOpensANameBoxWithFocusAndCreatesAPlan() {
        page.navigate(url("/plans"));
        createPlanWithHauler();
        var first = page.url();

        page.click("#new-plan-toggle");

        // the cursor is already in the name box, so the name can be typed at once
        assertThat(page.locator("#new-plan-name-here")).isFocused();
        page.keyboard().type("Second from the editor");
        page.keyboard().press("Enter");
        page.waitForURL(u -> !u.equals(first) && u.contains("/plans/"));
        assertThat(page.locator("h1")).hasText("Second from the editor");
        assertThat(page.locator("#no-products")).isVisible();
    }

    @Test
    void theNewMenuClosesWhenThePageIsClickedElsewhere() {
        page.navigate(url("/plans"));
        createPlanWithHauler();

        page.click("#new-plan-toggle");
        assertThat(page.locator("#new-plan-name-here")).isVisible();
        page.click("h1");

        assertThat(page.locator("#new-plan-name-here")).not().isVisible();
    }

    // ---- removing a product

    @Test
    void removingAProductTakesItOutAndUpdatesTheTotalAndUndoBringsItBack() {
        page.navigate(url("/plans"));
        createPlanWithHauler();
        var quoted = page.locator("#plan-total").innerText();

        page.click("#remove-1001");

        assertThat(page.locator("#no-products")).isVisible();
        assertThat(page.locator("#quantity-1001")).hasCount(0);
        assertThat(page.locator("#undo-bar")).containsText("Removed Test Hauler (1 unit).");
        assertThat(page.locator("#plan-total")).not().hasText(quoted);

        page.click("#undo-remove");

        assertThat(page.locator("#quantity-1001")).hasValue("1");
        assertThat(page.locator("#undo-bar")).not().containsText("Removed");
        assertThat(page.locator("#plan-total")).hasText(quoted);
    }

    @Test
    void unitsTheBoxCannotHoldAreRefusedWithAReasonAndTheOldNumberReturns() {
        page.navigate(url("/plans"));
        createPlanWithHauler();
        var quoted = page.locator("#plan-total").innerText();

        page.locator("#quantity-1001").fill("0");
        page.locator("#quantity-1001").blur();

        assertThat(page.locator("#error-toast")).containsText("at least 1");
        assertThat(page.locator("#quantity-1001")).hasValue("1");
        assertThat(page.locator("#plan-total")).hasText(quoted);

        page.locator("#quantity-1001").fill("1000001");
        page.locator("#quantity-1001").blur();

        assertThat(page.locator("#error-toast")).containsText("at most 1,000,000");
        assertThat(page.locator("#quantity-1001")).hasValue("1");

        // a good number is still saved
        page.locator("#quantity-1001").fill("2");
        page.locator("#quantity-1001").blur();
        assertThat(page.locator("#saved-1001")).containsText("Saved");
        assertThat(page.locator("#plan-total")).not().hasText(quoted);
    }

    // ---- archiving and deleting plans

    private void createPlan(String name) {
        page.navigate(url("/plans"));
        page.fill("#new-plan-name", name);
        page.click("#new-plan");
        page.waitForURL("**/plans/*");
    }

    private com.microsoft.playwright.Locator row(String name) {
        return page.locator("#plan-list tbody tr", new Page.LocatorOptions().setHasText(name));
    }

    @Test
    void archivingFromTheListHidesThePlanAndShowArchivedBringsItBackAndRestoreUndoesIt() {
        createPlan("Old plan");
        page.navigate(url("/plans"));
        markPage();

        row("Old plan").getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                new com.microsoft.playwright.Locator.GetByRoleOptions().setName("Archive")).click();

        assertThat(page.locator("#all-archived")).isVisible();
        assertThat(row("Old plan")).hasCount(0);

        page.check("#show-archived");
        assertThat(row("Old plan")).hasCount(1);
        assertThat(row("Old plan")).containsText("Archived");
        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*/plans\\?archived=1"));

        page.uncheck("#show-archived");
        assertThat(row("Old plan")).hasCount(0);

        page.check("#show-archived");
        row("Old plan").getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                new com.microsoft.playwright.Locator.GetByRoleOptions().setName("Restore")).click();

        // back on the list as it was, with archived plans still shown, and the plan active again
        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*/plans\\?archived=1"));
        assertThat(row("Old plan")).hasCount(1);
        assertThat(row("Old plan")).not().containsText("Archived");
    }

    @Test
    void deleteAsksForConfirmationNamingThePlanAndOnlyDeletesWhenConfirmed() {
        createPlan("Doomed plan");
        page.navigate(url("/plans"));

        row("Doomed plan").locator("summary", new com.microsoft.playwright.Locator.LocatorOptions().setHasText("Delete")).click();

        assertThat(row("Doomed plan")).containsText("Delete \"Doomed plan\" permanently?");
        // clicking elsewhere closes the question and nothing is deleted
        page.click("h1");
        assertThat(row("Doomed plan").locator("form[action$='/delete']")).not().isVisible();
        assertThat(row("Doomed plan")).hasCount(1);

        row("Doomed plan").locator("summary", new com.microsoft.playwright.Locator.LocatorOptions().setHasText("Delete")).click();
        row("Doomed plan").getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                new com.microsoft.playwright.Locator.GetByRoleOptions().setName("Delete plan")).click();

        assertThat(page.locator("#no-plans")).isVisible();
        assertThat(page.locator("#plan-list tbody tr")).hasCount(0);
    }

    @Test
    void archivingFromThePlanPageGoesToTheListAndRestoringFromItStaysOnThePlan() {
        createPlan("Page plan");
        var address = page.url();

        page.click("#archive-plan");

        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*/plans$"));
        assertThat(page.locator("#all-archived")).isVisible();

        page.navigate(address);
        assertThat(page.locator("#archived-badge")).isVisible();
        assertThat(page.locator("#archive-plan")).hasCount(0);
        page.click("#restore-plan");

        assertThat(page).hasURL(address);
        assertThat(page.locator("#archived-badge")).hasCount(0);
        assertThat(page.locator("#archive-plan")).isVisible();
    }

    @Test
    void deletingFromThePlanPageAsksFirstAndThenGoesToTheList() {
        createPlan("Page doomed");

        page.click("#delete-plan");
        assertThat(page.locator("#delete-plan-confirm")).isVisible();
        page.click("#delete-plan-confirm");

        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*/plans$"));
        assertThat(page.locator("#no-plans")).isVisible();
    }

    @Test
    void theNewAndDeleteMenusOpenAboveTheStickyTotalBarSoTheirButtonsCanBeClicked() {
        page.navigate(url("/plans"));
        createPlanWithHauler();

        // both open under the header, where the sticky total bar is: a click must reach the button and not the bar
        page.click("#new-plan-toggle");
        page.locator("#new-plan-menu button[type=submit]").click(new com.microsoft.playwright.Locator.ClickOptions().setTrial(true));
        page.click("h1");
        page.click("#delete-plan");
        page.locator("#delete-plan-confirm").click(new com.microsoft.playwright.Locator.ClickOptions().setTrial(true));
    }
}
