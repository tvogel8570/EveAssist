package com.eveassist.industry.internal.project.defaults.web;

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

/** The project defaults page in a real browser: Set all, the unsaved marks, saving, and the reset that asks first. */
@Import(TestSignIn.class)
class ProjectDefaultsBrowserIT extends IntegrationTest {

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
    void openThePageFromTheProjectsList() {
        context = browser.newContext();
        page = context.newPage();
        page.onPageError(pageErrors::add);
        page.navigate(url("/test-sign-in?sub=" + newSub()));
        page.navigate(url("/projects"));
        page.click("#project-defaults-link");
        page.waitForURL("**/project-defaults");
    }

    @AfterEach
    void close() {
        context.close();
        assertThat(pageErrors).isEmpty();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private List<String> levels() {
        return page.locator("#default-skills select[data-skill]").all().stream().map(skill -> skill.inputValue()).toList();
    }

    @Test
    void setAllGivesEverySkillOfItsGroupTheLevelAndMarksThePageAsUnsaved() {
        page.click("#default-skills > summary");
        assertThat(levels()).containsOnly("0");
        assertThat(page.locator("[data-unsaved-note]").isVisible()).isFalse();

        page.selectOption("#set-all-0", "4");

        // every skill of the first group, and the dropdown back at the dots
        var firstGroup = page.locator("[data-skill-group]").first();
        assertThat(firstGroup.locator("select[data-skill]").all()).allSatisfy(skill -> assertThat(skill.inputValue()).isEqualTo("4"));
        assertThat(page.inputValue("#set-all-0")).isEmpty();
        assertThat(page.locator("[data-unsaved-note]").isVisible()).isTrue();
        assertThat(page.locator("#default-skills [data-section-unsaved]").isVisible()).isTrue();
    }

    @Test
    void whatWasSavedIsThereAgainAndResettingAsksFirst() {
        page.fill("input[name=contractFee]", "25000");
        page.click("#default-skills > summary");
        page.selectOption("#set-all-0", "5");
        page.click("#save-defaults");
        page.locator("#defaults-saved").waitFor();

        assertThat(page.inputValue("input[name=contractFee]")).isEqualTo("25000");
        assertThat(levels()).contains("5");
        assertThat(page.locator("#never-saved").count()).isZero();

        page.click("#reset-defaults > summary");
        assertThat(page.inputValue("input[name=contractFee]")).isEqualTo("25000");
        page.click("#reset-defaults-confirm");
        page.locator("#never-saved").waitFor();

        assertThat(page.inputValue("input[name=contractFee]")).isEqualTo("10000");
        assertThat(levels()).containsOnly("0");
    }
}
