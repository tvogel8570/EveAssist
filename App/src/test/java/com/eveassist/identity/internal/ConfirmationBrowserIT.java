package com.eveassist.identity.internal;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.eveassist.IntegrationTest;
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
 * Whatever removes something asks first, the same way everywhere: a green Cancel that has the focus, so that Enter removes nothing, beside the
 * button that does it. Deleting the account asks on a page of its own, where the button waits for the box.
 */
@Import(TestSignIn.class)
class ConfirmationBrowserIT extends IntegrationTest {

    private static Playwright playwright;
    private static Browser browser;

    @LocalServerPort
    int port;

    @Autowired
    JdbcClient jdbc;

    private BrowserContext context;
    private Page page;
    private final List<String> pageErrors = new ArrayList<>();
    private UUID owner;

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
        var sub = newSub();
        owner = viewerFor(sub).accountId().value();
        context = browser.newContext();
        page = context.newPage();
        page.onPageError(pageErrors::add);
        page.navigate(url("/test-sign-in?sub=" + sub));
    }

    @AfterEach
    void close() {
        context.close();
        assertThat(pageErrors).isEmpty();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private boolean isSuccessColoured(Locator button) {
        return (Boolean) button.evaluate("""
                element => {
                    const probe = document.body.appendChild(document.createElement('button'));
                    probe.className = 'btn btn-success';
                    const green = getComputedStyle(probe).backgroundColor;
                    probe.remove();
                    return getComputedStyle(element).backgroundColor === green;
                }""");
    }

    private int facilities() {
        return jdbc.sql("SELECT count(*) FROM facility WHERE owner_account_id = :owner").param("owner", owner).query(Integer.class).single();
    }

    @Test
    void aConfirmationOpensOnAGreenCancelThatEnterPressesAndThatRemovesNothing() {
        var facility = UUID.randomUUID();
        jdbc.sql("INSERT INTO facility (public_id, owner_account_id, name) VALUES (:id, :owner, 'Azbel')").param("id", facility)
                .param("owner", owner).update();
        page.navigate(url("/facilities"));
        // the facility's own section holds the delete menu
        page.evaluate("document.querySelectorAll('details:not(.dropdown)').forEach(section => section.open = true)");
        var open = page.locator("#delete-" + facility);
        var cancel = page.locator("details.dropdown:has(#delete-" + facility + ") button[data-cancel]");

        // opened from the keyboard, as someone pressing Enter twice by habit would
        open.focus();
        page.keyboard().press("Enter");

        assertThat(cancel).isVisible();
        assertThat(cancel).isFocused();
        assertThat(cancel).hasText("Cancel");
        assertThat(isSuccessColoured(cancel)).isTrue();

        page.keyboard().press("Enter");

        assertThat(cancel).isHidden();
        assertThat(open).isFocused();
        assertThat(facilities()).isEqualTo(1);

        open.click();
        assertThat(cancel).isFocused();
        cancel.click();
        assertThat(cancel).isHidden();

        open.click();
        page.waitForNavigation(() -> page.click("#delete-confirm-" + facility));
        assertThat(facilities()).isZero();
    }

    @Test
    void theCharactersOwnConfirmationsHaveTheSameCancel() {
        jdbc.sql("""
                INSERT INTO character (public_id, owner_account_id, eve_character_id, name, owner_hash, scopes, state)
                VALUES (:id, :owner, :eve, 'Careful Character', 'hash', :scopes, 'ACTIVE')
                """)
                .param("id", UUID.randomUUID()).param("owner", owner).param("eve", 1_900_000_000L + java.util.concurrent.ThreadLocalRandom.current().nextInt(90_000_000))
                // a permission no feature that is on needs, so that it can be dropped
                .param("scopes", new String[] {"esi-assets.read_assets.v1"}).update();
        page.navigate(url("/characters"));
        var card = page.locator("#characters > .card", new Page.LocatorOptions().setHasText("Careful Character"));

        for (var opens : List.of("Remove", "Drop unused permissions")) {
            var menu = card.locator("details", new Locator.LocatorOptions().setHas(page.locator("summary", new Page.LocatorOptions().setHasText(opens))));
            menu.locator("summary").click();

            var cancel = menu.locator("button[data-cancel]");
            assertThat(cancel).isFocused();
            assertThat(isSuccessColoured(cancel)).isTrue();
            page.keyboard().press("Enter");
            assertThat(cancel).isHidden();
        }
        assertThat(card).isVisible();
    }

    @Test
    void deletingTheAccountWaitsForTheBoxAndKeepingItIsTheDefault() {
        page.navigate(url("/account/delete"));
        var delete = page.locator("#confirm-delete");
        var keep = page.locator("#cancel-delete");

        assertThat(keep).isFocused();
        assertThat(isSuccessColoured(keep)).isTrue();
        assertThat(delete).isDisabled();

        page.check("#confirm");
        assertThat(delete).isEnabled();
        page.uncheck("#confirm");
        assertThat(delete).isDisabled();

        // back to the page with the box still ticked, as a browser may show it again: the button follows the box
        page.check("#confirm");
        page.evaluate("document.querySelector('#confirm-delete').disabled = true; window.dispatchEvent(new PageTransitionEvent('pageshow', {persisted: true}))");
        assertThat(delete).isEnabled();
    }
}
