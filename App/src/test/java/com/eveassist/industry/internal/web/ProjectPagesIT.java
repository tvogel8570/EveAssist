package com.eveassist.industry.internal.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eveassist.IntegrationTest;
import com.eveassist.industry.PlanId;
import com.eveassist.industry.ProjectId;
import com.eveassist.industry.internal.application.AddProductCommand;
import com.eveassist.industry.internal.application.CreatePlanCommand;
import com.eveassist.industry.internal.application.IssueQuoteCommand;
import com.eveassist.industry.internal.application.PlanEditing;
import com.eveassist.industry.internal.application.PlanEntry;
import com.eveassist.industry.internal.application.ProjectQuoting;
import com.eveassist.market.AdjustedPriceService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * One project's page with each state it has to show and each change it takes, because Thymeleaf checks nothing when the code compiles. The
 * project is two Haulers started from a plan, in a facility the test inserts, as {@code TwoHaulers} does for the use-case tests.
 */
@AutoConfigureMockMvc
class ProjectPagesIT extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    PlanEditing planEditing;

    @Autowired
    PlanEntry planEntry;

    @Autowired
    ProjectQuoting quoting;

    @Autowired
    AdjustedPriceService adjusted;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void adjustedPrices() {
        esi.adjustedPrice(TRITANIUM.value(), "4");
        esi.adjustedPrice(PILOTS_MINERAL.value(), "8");
        esi.adjustedPrice(COMPONENT.value(), "100");
        adjusted.refresh();
    }

    private RequestPostProcessor signedIn(String sub) {
        accounts.signedIn(sub, "Test Pilot");
        return oidcLogin().idToken(token -> token.subject(sub)).authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private static ResultActions rendered(ResultActions result) throws Exception {
        return result.andExpect(content().string(not(containsString("??"))));
    }

    private PlanId planWithAHauler(String sub) {
        var viewer = viewerFor(sub);
        var id = planEditing.create(viewer, new CreatePlanCommand("Haulers"));
        planEntry.addProduct(viewer, new AddProductCommand(id, HAULER, 1));
        return id;
    }

    /** Starts a project the way the list page does, which costs it, and answers with its id. */
    private UUID started(String sub) throws Exception {
        var plan = planWithAHauler(sub);
        var location = mvc.perform(post("/projects").param("plan", plan.value().toString()).with(signedIn(sub)).with(csrf()))
                .andReturn().getResponse().getRedirectedUrl();
        return UUID.fromString(location.substring("/projects/".length()));
    }

    private UUID azbel(String sub) {
        var viewer = viewerFor(sub);
        var id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO facility (public_id, owner_account_id, name, structure_material_percent, structure_time_percent,
                    structure_cost_percent, system_cost_index_percent, scc_surcharge_percent)
                VALUES (:id, :owner, 'Test Azbel', 1, 20, 4, 2, 4)
                """).param("id", id).param("owner", viewer.accountId().value()).update();
        return id;
    }

    private long versionOf(UUID id) {
        return jdbc.sql("SELECT version FROM project WHERE public_id = :id").param("id", id).query(Long.class).single();
    }

    private ResultActions page(String sub, UUID id) throws Exception {
        return rendered(mvc.perform(get("/projects/" + id).with(signedIn(sub))));
    }

    /** A costed project in the facility, ready to be quoted. */
    private UUID quotable(String sub) throws Exception {
        var id = started(sub);
        var facility = azbel(sub);
        mvc.perform(post("/projects/" + id + "/config").param("expectedVersion", String.valueOf(versionOf(id)))
                .param("defaultFacility", facility.toString()).with(signedIn(sub)).with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(post("/projects/" + id + "/cost").with(signedIn(sub)).with(csrf())).andExpect(status().is3xxRedirection());
        return id;
    }

    @Test
    void aCostedProjectShowsItsCostItsQuoteItsItemsAndWhatBlocksTheQuote() throws Exception {
        var sub = newSub();
        var id = started(sub);

        page(sub, id).andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"state-badge\"")))
                .andExpect(content().string(containsString("id=\"delivered-cost-total\"")))
                .andExpect(content().string(containsString("id=\"quote-price\"")))
                .andExpect(content().string(containsString("id=\"item-" + HAULER.value() + "\"")))
                // no facility was chosen, so the costing warns and the quote cannot be issued
                .andExpect(content().string(containsString("id=\"warnings\"")))
                .andExpect(content().string(containsString("id=\"cannot-issue\"")))
                // no facility yet: the way to make one is the last entry of the Default facility dropdown, which leads to the Facilities page
                .andExpect(content().string(containsString("value=\"__new\"")))
                .andExpect(content().string(containsString("data-goto=\"/facilities?project=" + id + "\"")))
                .andExpect(content().string(not(containsString("data-section=\"facilities\""))))
                // a costing that is current can still be made again, because prices and indices move
                .andExpect(content().string(containsString("id=\"recost\"")));
    }

    @Test
    void anotherAccountsProjectAndAMissingOneAreNotFound() throws Exception {
        var id = started(newSub());

        mvc.perform(get("/projects/" + id).with(signedIn(newSub()))).andExpect(status().isNotFound());
        mvc.perform(get("/projects/" + UUID.randomUUID()).with(signedIn(newSub()))).andExpect(status().isNotFound());
    }

    @Test
    void changingASettingMakesTheCostingOutOfDateUntilItIsCostedAgain() throws Exception {
        var sub = newSub();
        var id = quotable(sub);
        page(sub, id).andExpect(content().string(not(containsString("id=\"banner-stale\""))))
                .andExpect(content().string(not(containsString("id=\"cannot-issue\""))));

        mvc.perform(post("/projects/" + id + "/config").param("expectedVersion", String.valueOf(versionOf(id)))
                        .param("mode", "MARGIN").param("marginPercent", "25").with(signedIn(sub)).with(csrf()))
                .andExpect(redirectedUrl("/projects/" + id));
        page(sub, id).andExpect(content().string(containsString("id=\"banner-stale\"")))
                .andExpect(content().string(containsString("You changed something after the last costing.")))
                .andExpect(content().string(containsString("id=\"out-of-date-badge\"")))
                .andExpect(content().string(containsString("id=\"cannot-issue\"")))
                .andExpect(content().string(containsString("value=\"25\"")));

        mvc.perform(post("/projects/" + id + "/cost").with(signedIn(sub)).with(csrf()));
        page(sub, id).andExpect(content().string(not(containsString("id=\"banner-stale\""))))
                .andExpect(content().string(not(containsString("id=\"cannot-issue\""))));
    }

    @Test
    void aValueTheDomainRefusesComesBackAsAMessageAndSavesNothing() throws Exception {
        var sub = newSub();
        var id = quotable(sub);
        var before = versionOf(id);

        mvc.perform(post("/projects/" + id + "/config").param("expectedVersion", String.valueOf(before))
                        .param("marginPercent", "100").with(signedIn(sub)).with(csrf()))
                .andExpect(redirectedUrl("/projects/" + id)).andExpect(flash().attribute("error", "invalid"));
        mvc.perform(post("/projects/" + id + "/config").param("expectedVersion", String.valueOf(before))
                        .param("marginPercent", "abc").with(signedIn(sub)).with(csrf()))
                .andExpect(redirectedUrl("/projects/" + id)).andExpect(flash().attribute("error", "invalid"));
        mvc.perform(post("/projects/" + id + "/items/" + HAULER.value()).param("expectedVersion", String.valueOf(before))
                        .param("me", "11").param("te", "20").with(signedIn(sub)).with(csrf()))
                .andExpect(redirectedUrl("/projects/" + id)).andExpect(flash().attribute("error", "invalid"));
        org.assertj.core.api.Assertions.assertThat(versionOf(id)).isEqualTo(before);
    }

    @Test
    void aChangeFromAnOldPageIsRefusedAsAConflict() throws Exception {
        var sub = newSub();
        var id = quotable(sub);

        mvc.perform(post("/projects/" + id + "/config").param("expectedVersion", String.valueOf(versionOf(id) - 1))
                        .param("marginPercent", "30").with(signedIn(sub)).with(csrf()))
                .andExpect(redirectedUrl("/projects/" + id)).andExpect(flash().attribute("error", "changed"));
    }

    @Test
    void anItemsMeAndTeAreSavedAndShownAgain() throws Exception {
        var sub = newSub();
        var id = quotable(sub);

        mvc.perform(post("/projects/" + id + "/items/" + HAULER.value()).param("expectedVersion", String.valueOf(versionOf(id)))
                        .param("me", "8").param("te", "16").param("facility", "").param("blueprintCostPerRun", "1500")
                        .with(signedIn(sub)).with(csrf()))
                .andExpect(redirectedUrl("/projects/" + id));
        page(sub, id).andExpect(content().string(containsString("name=\"me\" value=\"8\"")))
                .andExpect(content().string(containsString("name=\"te\" value=\"16\"")))
                .andExpect(content().string(containsString("value=\"1500\"")))
                .andExpect(content().string(containsString("id=\"banner-stale\"")));
    }

    @Test
    void issuingAQuoteFreezesTheProjectAndANewVersionOpensItAgain() throws Exception {
        var sub = newSub();
        var id = quotable(sub);

        mvc.perform(post("/projects/" + id + "/quotes").param("expectedVersion", String.valueOf(versionOf(id)))
                        .param("buyer", "Kestrel <b>Dawn</b>").param("note", "By contract")
                        .param("expires", LocalDate.now().plusDays(7).toString()).with(signedIn(sub)).with(csrf()))
                .andExpect(redirectedUrl("/projects/" + id));
        page(sub, id).andExpect(content().string(containsString("id=\"banner-quoted\"")))
                .andExpect(content().string(not(containsString("id=\"recost\""))))
                .andExpect(content().string(containsString("id=\"quote-1\"")))
                .andExpect(content().string(containsString("Kestrel &lt;b&gt;Dawn&lt;/b&gt;")))
                .andExpect(content().string(not(containsString("<b>Dawn</b>"))))
                .andExpect(content().string(not(containsString("id=\"issue-form\""))))
                .andExpect(content().string(containsString("id=\"new-version-1\"")));

        // a quoted project cannot be changed, whatever a form sends
        mvc.perform(post("/projects/" + id + "/config").param("expectedVersion", String.valueOf(versionOf(id)))
                        .param("marginPercent", "30").with(signedIn(sub)).with(csrf()))
                .andExpect(flash().attribute("error", "locked"));

        mvc.perform(post("/projects/" + id + "/quotes/1/new-version").param("expectedVersion", String.valueOf(versionOf(id)))
                        .with(signedIn(sub)).with(csrf()))
                .andExpect(redirectedUrl("/projects/" + id));
        page(sub, id).andExpect(content().string(not(containsString("id=\"banner-quoted\""))))
                .andExpect(content().string(containsString("id=\"banner-uncosted\"")));
    }

    @Test
    void aQuoteCannotBeIssuedFromAnOutOfDateCostingNorWithAnExpiryInThePast() throws Exception {
        var sub = newSub();
        var id = quotable(sub);
        mvc.perform(post("/projects/" + id + "/quotes").param("expectedVersion", String.valueOf(versionOf(id)))
                        .param("expires", LocalDate.now().minusDays(1).toString()).with(signedIn(sub)).with(csrf()))
                .andExpect(flash().attribute("error", "invalidQuote"));

        mvc.perform(post("/projects/" + id + "/config").param("expectedVersion", String.valueOf(versionOf(id)))
                .param("marginPercent", "30").with(signedIn(sub)).with(csrf()));
        mvc.perform(post("/projects/" + id + "/quotes").param("expectedVersion", String.valueOf(versionOf(id)))
                        .with(signedIn(sub)).with(csrf()))
                .andExpect(flash().attribute("error", "outOfDate"));
    }

    @Test
    void aQuoteThatHasRunOutIsShownAsExpiredAndDeletingTheOnlyQuoteReopensTheProject() throws Exception {
        var sub = newSub();
        var id = quotable(sub);
        // an issued quote is frozen in the database, so it cannot be backdated: this one runs out a second after it is issued
        quoting.issue(viewerFor(sub), new IssueQuoteCommand(new ProjectId(id), versionOf(id), "Buyer", null,
                Instant.now().plusSeconds(1)));
        Thread.sleep(1_500);

        page(sub, id).andExpect(content().string(containsString("id=\"banner-expired\"")));

        mvc.perform(post("/projects/" + id + "/quotes/1/delete").with(signedIn(sub)).with(csrf()))
                .andExpect(redirectedUrl("/projects/" + id));
        page(sub, id).andExpect(content().string(not(containsString("id=\"quote-1\""))))
                .andExpect(content().string(not(containsString("id=\"banner-expired\""))))
                .andExpect(content().string(containsString("id=\"issue-form\"")));
    }

    @Test
    void costingAProjectWithNoProductsSaysSo() throws Exception {
        var sub = newSub();
        var viewer = viewerFor(sub);
        var empty = planEditing.create(viewer, new CreatePlanCommand("Empty"));
        var location = mvc.perform(post("/projects").param("plan", empty.value().toString()).with(signedIn(sub)).with(csrf()))
                .andReturn().getResponse().getRedirectedUrl();
        var id = UUID.fromString(location.substring("/projects/".length()));

        page(sub, id).andExpect(content().string(containsString("id=\"banner-uncosted\"")))
                .andExpect(content().string(containsString("id=\"no-price\"")));
        mvc.perform(post("/projects/" + id + "/cost").with(signedIn(sub)).with(csrf()))
                .andExpect(flash().attribute("error", "nothingToCost"));
    }

    /** Reading G16: the game counts a material ESI gives no adjusted price as 0, so the cost is right and the quote is not blocked. */
    @Test
    void aMaterialWithNoAdjustedPriceIsNamedInANoticeAndTheQuoteCanStillBeIssued() throws Exception {
        var sub = newSub();
        esi.reset();
        esi.adjustedPrice(TRITANIUM.value(), "4");
        esi.adjustedPrice(COMPONENT.value(), "100");
        adjusted.refresh();
        var id = quotable(sub);

        page(sub, id).andExpect(content().string(containsString("id=\"warnings\"")))
                .andExpect(content().string(containsString("No adjusted price for Pilot&#39;s Mineral. This only affects the job install cost.")))
                .andExpect(content().string(not(containsString("id=\"cannot-issue\""))))
                .andExpect(content().string(not(containsString("id=\"banner-stale\""))));
        mvc.perform(post("/projects/" + id + "/quotes").param("expectedVersion", String.valueOf(versionOf(id)))
                        .with(signedIn(sub)).with(csrf()))
                .andExpect(redirectedUrl("/projects/" + id));
        page(sub, id).andExpect(content().string(containsString("id=\"banner-quoted\"")));
    }

    // ---- sections and anchors ----

    private static org.hamcrest.Matcher<String> tagOf(String id, String attribute) {
        // the tag is one line, and its class list holds a ">" (an arbitrary variant), so the tag is read to the end of the line
        return org.hamcrest.Matchers.matchesPattern("(?s).*<details id=\"" + id + "\"[^\\n]*" + attribute + "[^\\n]*\\n.*");
    }

    @Test
    void everyLeftHandSectionCanBeClosedAndTheRightHandCardCannot() throws Exception {
        var sub = newSub();
        var id = quotable(sub);

        var result = page(sub, id);
        for (var section : List.of("settings", "items", "jobs", "delivered-cost")) {
            result.andExpect(content().string(tagOf(section, "data-section=\"" + section + "\"")));
        }
        // the quote card is the part that stays in view, so it is not a section
        result.andExpect(content().string(not(containsString("data-section=\"quote-card\""))));
    }

    /** The attribute the page sends an open section with, bare (the warnings) or as open="open" (a section opened after a refused change). */
    private static final String OPEN = "\\sopen(?:[\\s=>])";

    @Test
    void sectionsStartClosedExceptTheOneWithWarnings() throws Exception {
        var sub = newSub();
        var id = started(sub);

        var result = page(sub, id).andExpect(content().string(tagOf("warnings", OPEN)));
        for (var section : List.of("settings", "items", "jobs", "delivered-cost")) {
            result.andExpect(content().string(not(tagOf(section, OPEN))));
        }
    }

    @Test
    void aNoticeThatBlocksNothingStillOpensTheWarningsSectionByDefault() throws Exception {
        var sub = newSub();
        esi.reset();
        esi.adjustedPrice(TRITANIUM.value(), "4");
        esi.adjustedPrice(COMPONENT.value(), "100");
        adjusted.refresh();
        var id = quotable(sub);

        page(sub, id).andExpect(content().string(tagOf("warnings", OPEN)))
                .andExpect(content().string(not(tagOf("warnings", "data-keep-open"))));
    }

    @Test
    void everySectionHeaderHasAnIndicatorThatTurnsWhenItIsOpen() throws Exception {
        var sub = newSub();
        // a project with warnings, so that all five sections are there
        var id = started(sub);

        var html = page(sub, id).andReturn().getResponse().getContentAsString();
        // one chevron for each of the five sections: the header is a flex box, which leaves the browser's own triangle out
        assertThat(html.split("group-open/section:rotate-90", -1).length - 1).isEqualTo(5);
    }

    @Test
    void aWarningThatBlocksTheQuoteMakesItsSectionOpenWhateverTheUserKept() throws Exception {
        var sub = newSub();
        var id = started(sub);

        // no facility is chosen, so the costing warns and cannot be quoted
        page(sub, id).andExpect(content().string(tagOf("warnings", "data-keep-open")))
                .andExpect(content().string(containsString("Blocks the quote")));
    }

    @Test
    void aNoticeThatBlocksNothingLeavesItsSectionToTheUser() throws Exception {
        var sub = newSub();
        esi.reset();
        esi.adjustedPrice(TRITANIUM.value(), "4");
        esi.adjustedPrice(COMPONENT.value(), "100");
        adjusted.refresh();
        var id = quotable(sub);

        page(sub, id).andExpect(content().string(containsString("id=\"warnings\"")))
                .andExpect(content().string(not(tagOf("warnings", "data-keep-open"))))
                .andExpect(content().string(not(containsString("Blocks the quote"))));
    }

    @Test
    void aSaveReturnsToThePartOfThePageItWasMadeIn() throws Exception {
        var sub = newSub();
        var id = quotable(sub);
        var version = String.valueOf(versionOf(id));

        mvc.perform(post("/projects/" + id + "/config").param("expectedVersion", version).param("contractFee", "20000")
                        .param("anchor", "settings").with(signedIn(sub)).with(csrf()))
                .andExpect(redirectedUrl("/projects/" + id + "#settings"));
        mvc.perform(post("/projects/" + id + "/items/" + HAULER.value()).param("expectedVersion", String.valueOf(versionOf(id)))
                        .param("me", "9").param("te", "18").param("anchor", "item-" + HAULER.value()).with(signedIn(sub)).with(csrf()))
                .andExpect(redirectedUrl("/projects/" + id + "#item-" + HAULER.value()));
        mvc.perform(post("/projects/" + id + "/cost").param("anchor", "delivered-cost").with(signedIn(sub)).with(csrf()))
                .andExpect(redirectedUrl("/projects/" + id + "#delivered-cost"));
        mvc.perform(post("/projects/" + id + "/cost").with(signedIn(sub)).with(csrf()))
                .andExpect(redirectedUrl("/projects/" + id));
    }

    @Test
    void anAnchorThatIsNotAnIdTheWayThePageWritesThemIsLeftOut() throws Exception {
        var sub = newSub();
        var id = quotable(sub);

        for (var anchor : List.of("a b", "x?y=1", "//example.com", "1abc", "a#b", "a".repeat(41))) {
            mvc.perform(post("/projects/" + id + "/cost").param("anchor", anchor).with(signedIn(sub)).with(csrf()))
                    .andExpect(redirectedUrl("/projects/" + id));
        }
    }

    // ---- the text and the CSV of an issued quote ----

    /** Issues the quote of a costed, quotable project the way the quote card does. */
    private void issued(String sub, UUID id, String buyer, String note) throws Exception {
        mvc.perform(post("/projects/" + id + "/quotes").param("expectedVersion", String.valueOf(versionOf(id)))
                .param("buyer", buyer).param("note", note).param("expires", LocalDate.now().plusDays(7).toString())
                .with(signedIn(sub)).with(csrf())).andExpect(redirectedUrl("/projects/" + id));
    }

    @Test
    void anIssuedQuoteOffersItsTextToCopyAndItsCsvAndShowsNothingOfWhatItCost() throws Exception {
        var sub = newSub();
        var id = quotable(sub);
        page(sub, id).andExpect(content().string(not(containsString("id=\"quote-text-1\""))));

        issued(sub, id, "Kestrel <b>Dawn</b>", "By contract");

        var html = page(sub, id).andExpect(content().string(containsString("id=\"copy-quote-1\"")))
                .andExpect(content().string(containsString("href=\"/projects/" + id + "/quotes/1/csv\"")))
                // the buyer's name is text, so it is escaped in the box
                .andExpect(content().string(containsString("Quote 1 for Kestrel &lt;b&gt;Dawn&lt;/b&gt;")))
                .andExpect(content().string(not(containsString("<b>Dawn</b>"))))
                .andReturn().getResponse().getContentAsString();
        var box = html.substring(html.indexOf("id=\"quote-text-1\""), html.indexOf("</textarea>", html.indexOf("id=\"quote-text-1\"")));
        assertThat(box).contains("Total: ").contains(" ISK").contains("By contract").contains("Valid until ").contains(" \u00d7 ").contains(" = ");
        // the project has no Jita price for the Hauler, so the lines are priced alike, and the page says so
        assertThat(html).contains("priced alike");
        assertThat(box).doesNotContainIgnoringCase("margin").doesNotContainIgnoringCase("profit").doesNotContainIgnoringCase("cost");
    }

    @Test
    void theCsvIsADownloadWithTheProductsAndTheTotalAndAFormulaInTheBuyersNameIsMadeSafe() throws Exception {
        var sub = newSub();
        var id = quotable(sub);
        issued(sub, id, "=HYPERLINK(\"http://example.com\")", "a, b");

        var response = mvc.perform(get("/projects/" + id + "/quotes/1/csv").with(signedIn(sub)))
                .andExpect(status().isOk()).andReturn().getResponse();

        assertThat(response.getContentType()).startsWith("text/csv");
        assertThat(response.getHeader("Content-Disposition")).startsWith("attachment; filename=\"quote-").endsWith("-1.csv\"");
        var body = response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body).startsWith("Quote,Buyer,Valid until,Note\r\n")
                .contains("Item,Quantity,Price (ISK),Extension (ISK)\r\n")
                .contains("\r\nTotal,,,")
                // a quote in front of the formula, and the comma of the note makes it a quoted field
                .contains("\"'=HYPERLINK(\"\"http://example.com\"\")\"").contains("\"a, b\"");
        assertThat(body).doesNotContainIgnoringCase("margin").doesNotContainIgnoringCase("profit");
    }

    @Test
    void aCsvForAQuoteThatDoesNotExistOrBelongsToAnotherAccountIsNotFound() throws Exception {
        var owner = newSub();
        var id = quotable(owner);
        issued(owner, id, "Buyer", "");

        mvc.perform(get("/projects/" + id + "/quotes/2/csv").with(signedIn(owner))).andExpect(status().isNotFound());
        mvc.perform(get("/projects/" + id + "/quotes/1/csv").with(signedIn(newSub()))).andExpect(status().isNotFound());
        mvc.perform(get("/projects/" + UUID.randomUUID() + "/quotes/1/csv").with(signedIn(owner))).andExpect(status().isNotFound());
    }

    // ---- checking a costing against the game's window ----

    @Test
    void theComparePageLaysTheCostingOutLikeTheGamesWindowInBothLayouts() throws Exception {
        var sub = newSub();
        var id = quotable(sub);

        rendered(mvc.perform(get("/projects/" + id + "/compare").with(signedIn(sub)))).andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"source\"")))
                .andExpect(content().string(containsString("Draft costing made")))
                .andExpect(content().string(not(containsString("id=\"out-of-date\""))))
                // layout A: the window and its two hovers, with the names of what the job uses
                .andExpect(content().string(containsString("Required materials")))
                .andExpect(content().string(containsString("Tritanium")))
                .andExpect(content().string(containsString("Job duration modifiers")))
                .andExpect(content().string(containsString("Estimated Items value (EIV)")))
                .andExpect(content().string(containsString("Total job cost")))
                // layout B: a row for each line with an input for what the game shows
                .andExpect(content().string(containsString("data-game")))
                .andExpect(content().string(containsString("data-kind=\"dur\"")))
                .andExpect(content().string(containsString("id=\"view-a\"")))
                .andExpect(content().string(containsString("id=\"view-b\"")))
                .andExpect(content().string(containsString("/js/compare.js")));
    }

    @Test
    void theComparePageSaysWhenTheProjectWasChangedAfterTheCostingAndWhenThereIsNoCosting() throws Exception {
        var sub = newSub();
        var id = quotable(sub);
        mvc.perform(post("/projects/" + id + "/config").param("expectedVersion", String.valueOf(versionOf(id)))
                .param("marginPercent", "25").with(signedIn(sub)).with(csrf()));

        rendered(mvc.perform(get("/projects/" + id + "/compare").with(signedIn(sub))))
                .andExpect(content().string(containsString("id=\"out-of-date\"")));

        var empty = planEditing.create(viewerFor(sub), new CreatePlanCommand("Empty"));
        var location = mvc.perform(post("/projects").param("plan", empty.value().toString()).with(signedIn(sub)).with(csrf()))
                .andReturn().getResponse().getRedirectedUrl();
        rendered(mvc.perform(get(location + "/compare").with(signedIn(sub)))).andExpect(content().string(containsString("id=\"not-costed\"")))
                .andExpect(content().string(not(containsString("data-game"))));
    }

    @Test
    void anIssuedQuoteCanBeComparedAndItsCostingIsTheFrozenOne() throws Exception {
        var sub = newSub();
        var id = quotable(sub);
        issued(sub, id, "Buyer", "");

        // a quoted project shows its latest quote by itself, and any quote can be asked for
        rendered(mvc.perform(get("/projects/" + id + "/compare").with(signedIn(sub)))).andExpect(content().string(containsString("Quote 1, frozen")))
                .andExpect(content().string(not(containsString("id=\"out-of-date\""))));
        rendered(mvc.perform(get("/projects/" + id + "/compare").param("quote", "1").with(signedIn(sub))))
                .andExpect(content().string(containsString("Quote 1, frozen")));
        mvc.perform(get("/projects/" + id + "/compare").param("quote", "2").with(signedIn(sub))).andExpect(status().isNotFound());
        // the project page leads to it, from the costing and from the quote
        page(sub, id).andExpect(content().string(containsString("id=\"compare-quote-1\"")))
                .andExpect(content().string(containsString("href=\"/projects/" + id + "/compare?quote=1\"")));
    }

    @Test
    void theProjectPageLeadsToTheComparisonAndAnotherAccountsProjectIsNotFound() throws Exception {
        var owner = newSub();
        var id = quotable(owner);

        page(owner, id).andExpect(content().string(containsString("id=\"compare-link\"")))
                .andExpect(content().string(containsString("href=\"/projects/" + id + "/compare\"")));
        mvc.perform(get("/projects/" + id + "/compare").with(signedIn(newSub()))).andExpect(status().isNotFound());
        mvc.perform(get("/projects/" + UUID.randomUUID() + "/compare").with(signedIn(owner))).andExpect(status().isNotFound());
    }

    @Test
    void theNamesOfTheItemsAreEscapedOnTheComparePage() throws Exception {
        var sub = newSub();
        var viewer = viewerFor(sub);
        var plan = planEditing.create(viewer, new CreatePlanCommand("Escaping"));
        planEntry.addProduct(viewer, new AddProductCommand(plan, SECOND_SHIP, 1));
        var location = mvc.perform(post("/projects").param("plan", plan.value().toString()).with(signedIn(sub)).with(csrf()))
                .andReturn().getResponse().getRedirectedUrl();

        rendered(mvc.perform(get(location + "/compare").with(signedIn(sub))))
                .andExpect(content().string(containsString("Second &lt;b&gt;Ship&lt;/b&gt;")))
                .andExpect(content().string(not(containsString("<b>Ship</b>"))));
    }
}
