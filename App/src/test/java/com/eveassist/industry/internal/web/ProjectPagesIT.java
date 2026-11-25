package com.eveassist.industry.internal.web;

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
                .andExpect(content().string(containsString("id=\"no-facilities\"")));
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
}
