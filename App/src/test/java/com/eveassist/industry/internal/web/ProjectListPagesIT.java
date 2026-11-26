package com.eveassist.industry.internal.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eveassist.IntegrationTest;
import com.eveassist.industry.PlanId;
import com.eveassist.industry.internal.plan.application.AddProductCommand;
import com.eveassist.industry.internal.plan.application.CreatePlanCommand;
import com.eveassist.industry.internal.plan.application.PlanEditing;
import com.eveassist.industry.internal.plan.application.PlanEntry;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Renders the Projects list with each case it has to handle, because Thymeleaf checks nothing when the code compiles. */
@AutoConfigureMockMvc
class ProjectListPagesIT extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    PlanEditing planEditing;

    @Autowired
    PlanEntry planEntry;

    @Autowired
    JdbcClient jdbc;

    private RequestPostProcessor signedIn(String sub) {
        accounts.signedIn(sub, "Test Pilot");
        return oidcLogin().idToken(token -> token.subject(sub)).authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private PlanId planWithAHauler(String sub, String name) {
        var viewer = viewerFor(sub);
        var id = planEditing.create(viewer, new CreatePlanCommand(name));
        planEntry.addProduct(viewer, new AddProductCommand(id, HAULER, 1));
        return id;
    }

    /** Starts a project the way the page does, and answers with its id. */
    private UUID started(String sub, PlanId plan, String name) throws Exception {
        var location = mvc.perform(post("/projects").param("plan", plan.value().toString()).param("name", name)
                        .with(signedIn(sub)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/projects/*"))
                .andReturn().getResponse().getRedirectedUrl();
        return UUID.fromString(location.substring("/projects/".length()));
    }

    private static ResultActions rendered(ResultActions result) throws Exception {
        return result.andExpect(content().string(not(containsString("??"))));
    }

    @Test
    void listWithNoProjectsSaysSoAndOffersToStartOne() throws Exception {
        rendered(mvc.perform(get("/projects").with(signedIn(newSub()))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"no-projects\"")))
                .andExpect(content().string(containsString("id=\"no-plans-to-start\"")));
    }

    @Test
    void startingFromAPlanCopiesItAndShowsItCostedWithTheNameEscaped() throws Exception {
        var sub = newSub();
        var plan = planWithAHauler(sub, "Plan <b>one</b>");
        var id = started(sub, plan, "Haulers <i>for Kestrel</i>");

        rendered(mvc.perform(get("/projects").with(signedIn(sub))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"project-row-" + id + "\"")))
                .andExpect(content().string(containsString("Haulers &lt;i&gt;for Kestrel&lt;/i&gt;")))
                .andExpect(content().string(not(containsString("<i>for Kestrel</i>"))))
                .andExpect(content().string(not(containsString("id=\"no-projects\""))))
                // started from a plan, so it is costed at once and the plan stays in the picker
                .andExpect(content().string(containsString("Costed")))
                .andExpect(content().string(containsString("Plan &lt;b&gt;one&lt;/b&gt;")));
    }

    @Test
    void aBlankNameTakesThePlansOwnName() throws Exception {
        var sub = newSub();
        var plan = planWithAHauler(sub, "Haulers for the corp");
        started(sub, plan, "  ");

        rendered(mvc.perform(get("/projects").with(signedIn(sub))))
                .andExpect(content().string(containsString("Haulers for the corp")));
    }

    @Test
    void cardsShowTheSameProjects() throws Exception {
        var sub = newSub();
        var id = started(sub, planWithAHauler(sub, "Plan"), "Card project");

        rendered(mvc.perform(get("/projects").param("view", "cards").with(signedIn(sub))))
                .andExpect(content().string(containsString("id=\"project-card-" + id + "\"")))
                .andExpect(content().string(not(containsString("id=\"project-row-" + id + "\""))));
    }

    @Test
    void theStateFilterNarrowsTheList() throws Exception {
        var sub = newSub();
        var id = started(sub, planWithAHauler(sub, "Plan"), "Costed one");

        rendered(mvc.perform(get("/projects").param("state", "costed").with(signedIn(sub))))
                .andExpect(content().string(containsString("id=\"project-row-" + id + "\"")));
        rendered(mvc.perform(get("/projects").param("state", "quoted").with(signedIn(sub))))
                .andExpect(content().string(not(containsString("id=\"project-row-" + id + "\""))))
                .andExpect(content().string(containsString("id=\"no-match\"")));
    }

    @Test
    void archivingHidesAProjectUntilArchivedAreShownAndRestoreBringsItBack() throws Exception {
        var sub = newSub();
        var id = started(sub, planWithAHauler(sub, "Plan"), "To archive");

        mvc.perform(post("/projects/" + id + "/archive").with(signedIn(sub)).with(csrf()))
                .andExpect(redirectedUrl("/projects"));
        rendered(mvc.perform(get("/projects").with(signedIn(sub))))
                .andExpect(content().string(not(containsString("id=\"project-row-" + id + "\""))));
        rendered(mvc.perform(get("/projects").param("archived", "1").with(signedIn(sub))))
                .andExpect(content().string(containsString("id=\"project-row-" + id + "\"")))
                .andExpect(content().string(containsString("Archived")));

        mvc.perform(post("/projects/" + id + "/restore").param("archived", "1").param("view", "cards")
                        .with(signedIn(sub)).with(csrf()))
                .andExpect(redirectedUrl("/projects?view=cards&archived=1"));
        rendered(mvc.perform(get("/projects").with(signedIn(sub))))
                .andExpect(content().string(containsString("id=\"project-row-" + id + "\"")));
    }

    @Test
    void deletingRemovesTheProject() throws Exception {
        var sub = newSub();
        var id = started(sub, planWithAHauler(sub, "Plan"), "To delete");

        mvc.perform(post("/projects/" + id + "/delete").with(signedIn(sub)).with(csrf()))
                .andExpect(redirectedUrl("/projects"));
        rendered(mvc.perform(get("/projects").with(signedIn(sub))))
                .andExpect(content().string(not(containsString("id=\"project-row-" + id + "\""))));
    }

    @Test
    void anotherAccountsProjectLooksLikeAMissingOne() throws Exception {
        var owner = newSub();
        var id = started(owner, planWithAHauler(owner, "Plan"), "Private");

        mvc.perform(post("/projects/" + id + "/delete").with(signedIn(newSub())).with(csrf()))
                .andExpect(status().isNotFound());
        rendered(mvc.perform(get("/projects").with(signedIn(owner))))
                .andExpect(content().string(containsString("id=\"project-row-" + id + "\"")));
    }

    @Test
    void startingFromAnotherAccountsPlanIsRefused() throws Exception {
        var plan = planWithAHauler(newSub(), "Not yours");

        mvc.perform(post("/projects").param("plan", plan.value().toString()).with(signedIn(newSub())).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void anOverlongNameIsRefused() throws Exception {
        var sub = newSub();
        var plan = planWithAHauler(sub, "Plan");

        mvc.perform(post("/projects").param("plan", plan.value().toString()).param("name", "x".repeat(101))
                        .with(signedIn(sub)).with(csrf()))
                .andExpect(status().isUnprocessableContent());
    }
}
