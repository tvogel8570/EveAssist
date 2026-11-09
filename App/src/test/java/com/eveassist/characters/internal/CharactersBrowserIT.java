package com.eveassist.characters.internal;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.eveassist.IntegrationTest;
import com.eveassist.characters.Feature;
import com.eveassist.identity.internal.TestSignIn;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/** The Characters page in a real browser: the htmx toggle that reloads the page, and the two-step remove. */
@Import(TestSignIn.class)
class CharactersBrowserIT extends IntegrationTest {

    private static Playwright playwright;
    private static Browser browser;

    @LocalServerPort
    int port;

    @Autowired
    CharacterRepository characters;

    @Autowired
    FeatureRepository features;

    @Autowired
    TokenCipher cipher;

    private BrowserContext context;
    private Page page;
    private String sub;
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
        sub = newSub();
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

    private void linkCharacter(String name) {
        var eveId = 2_000_000_000L + ThreadLocalRandom.current().nextInt(1_000_000_000);
        characters.link(viewerFor(sub).accountId(), eveId, name, "hash", Feature.INVENTORY.scopes(),
                cipher.encrypt(eveId, "refresh"));
    }

    @Test
    void turningInventoryOnReloadsThePageWithLinkingEnabled() {
        page.navigate(url("/characters"));
        assertThat(page.locator("#link-character")).isDisabled();
        assertThat(page.locator("#no-feature-hint")).isVisible();

        page.locator("#feature-INVENTORY").check();

        assertThat(page.locator("#link-character")).isEnabled();
        assertThat(page.locator("#no-feature-hint")).hasCount(0);
        assertThat(page.locator("#feature-INVENTORY")).isChecked();
        assertThat(features.turnedOn(viewerFor(sub).accountId())).containsExactly(Feature.INVENTORY);

        page.locator("#feature-INVENTORY").uncheck();

        assertThat(page.locator("#link-character")).isDisabled();
        assertThat(features.turnedOn(viewerFor(sub).accountId())).isEmpty();
    }

    @Test
    void removingACharacterNeedsAConfirmationAndThenDeletesIt() {
        linkCharacter("Alt One");
        linkCharacter("Alt Two");
        page.navigate(url("/characters"));
        var altOne = page.locator("#characters > .card", new Page.LocatorOptions().setHasText("Alt One"));
        assertThat(altOne).isVisible();

        altOne.locator("summary").click();
        assertThat(altOne.getByText("Remove Alt One?")).isVisible();
        assertThat(characters.listFor(viewerFor(sub).accountId())).hasSize(2);

        altOne.getByText("Yes, remove").click();

        assertThat(page.locator("#removed-message")).isVisible();
        assertThat(page.locator("#characters > .card", new Page.LocatorOptions().setHasText("Alt One"))).hasCount(0);
        assertThat(page.locator("#characters > .card", new Page.LocatorOptions().setHasText("Alt Two"))).hasCount(1);
        assertThat(characters.listFor(viewerFor(sub).accountId())).singleElement()
                .satisfies(c -> assertThat(c.name()).isEqualTo("Alt Two"));
    }
}
