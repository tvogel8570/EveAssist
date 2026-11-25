package com.eveassist.industry.internal.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.eveassist.IntegrationTest;
import com.eveassist.identity.internal.TestSignIn;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/** Sorting the project page's Jobs table by clicking a header, in a real browser: ascending, descending, none, one column at a time. */
@Import(TestSignIn.class)
class ProjectJobsSortBrowserIT extends IntegrationTest {

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
    void openAProjectWithSeveralJobs() {
        context = browser.newContext();
        page = context.newPage();
        page.onPageError(pageErrors::add);
        page.navigate(url("/test-sign-in?sub=" + newSub()));
        page.navigate(url("/plans"));
        page.fill("#new-plan-name", "Sorting plan");
        page.click("#new-plan");
        page.waitForURL("**/plans/*");
        addProduct("hauler", "Test Hauler");
        addProduct("nested", "Nested Ship");
        addProduct("second", "Second");
        page.navigate(url("/projects"));
        page.click("#start-project");
        page.click("#start-project-confirm");
        page.waitForURL("**/projects/*");
        // the Jobs section starts closed, and the sorting is in it
        page.click("#jobs > summary");
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

    private void sortBy(String header) {
        page.locator("#jobs th button", new Page.LocatorOptions().setHasText(header)).click();
    }

    private List<String> column(int index) {
        return page.locator("#jobs tbody tr td:nth-child(" + index + ")").allTextContents().stream().map(String::strip).toList();
    }

    private String ariaSort(String header) {
        return page.locator("#jobs th", new Page.LocatorOptions().setHas(
                page.locator("button", new Page.LocatorOptions().setHasText(header)))).getAttribute("aria-sort");
    }

    /** "2h", "1h 20m" and "8m" as seconds, which is how the Time column has to be ordered and not as the text it shows. */
    private static long seconds(String shown) {
        long total = 0;
        for (var part : shown.split(" ")) {
            var amount = Long.parseLong(part.substring(0, part.length() - 1));
            total += switch (part.charAt(part.length() - 1)) {
                case 'd' -> amount * 86_400;
                case 'h' -> amount * 3_600;
                case 'm' -> amount * 60;
                default -> amount;
            };
        }
        return total;
    }

    @Test
    void aHeaderSortsAscendingThenDescendingThenBackToThePagesOrder() {
        var original = column(1);
        assertThat(original).hasSizeGreaterThan(2);
        // the page does not give the items in alphabetical order, so a sort that did nothing would be seen
        assertThat(original).isNotEqualTo(original.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList());

        sortBy("Item");
        assertThat(column(1)).isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER);
        assertThat(ariaSort("Item")).isEqualTo("ascending");

        sortBy("Item");
        assertThat(column(1)).isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER.reversed());
        assertThat(ariaSort("Item")).isEqualTo("descending");

        sortBy("Item");
        assertThat(column(1)).isEqualTo(original);
        assertThat(ariaSort("Item")).isEqualTo("none");
    }

    @Test
    void aNumberColumnSortsByItsNumberAndNotByTheTextItShows() {
        // the page gives the times in ascending order, so the descending click is a sort that has to happen. As text, "1h 20m" would come
        // before "8m" and "2h" would come between them: ordering by what is shown would fail the ascending check below.
        assertThat(column(4).stream().map(ProjectJobsSortBrowserIT::seconds).toList()).isSortedAccordingTo(Comparator.naturalOrder());

        sortBy("Time");
        assertThat(column(4).stream().map(ProjectJobsSortBrowserIT::seconds).toList()).isSortedAccordingTo(Comparator.naturalOrder());

        sortBy("Time");
        assertThat(column(4).stream().map(ProjectJobsSortBrowserIT::seconds).toList())
                .isSortedAccordingTo(Comparator.<Long>naturalOrder().reversed());
        assertThat(column(4).getFirst()).isEqualTo("2h");
    }

    @Test
    void sortingAnotherColumnClearsTheFirstSoOnlyOneIsSorted() {
        var original = column(1);
        sortBy("Item");
        sortBy("Time");

        assertThat(ariaSort("Item")).isEqualTo("none");
        assertThat(ariaSort("Time")).isEqualTo("ascending");
        assertThat(page.locator("#jobs th[aria-sort=ascending], #jobs th[aria-sort=descending]").count()).isEqualTo(1);
        // four items take the same time and keep the page's order among themselves: the earlier sort by item is not kept as a second key
        assertThat(column(1)).isEqualTo(original);
        assertThat(page.locator("#jobs th[aria-sort=none] [data-sort-mark]").allTextContents()).allMatch(String::isBlank);
        assertThat(page.locator("#jobs th[aria-sort=ascending] [data-sort-mark]").textContent()).isEqualTo("\u25B2");
    }
}
