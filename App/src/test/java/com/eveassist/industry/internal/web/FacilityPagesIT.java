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

import static org.assertj.core.api.Assertions.assertThat;

import com.eveassist.IntegrationTest;
import com.eveassist.industry.internal.application.AddProductCommand;
import com.eveassist.industry.internal.application.CreatePlanCommand;
import com.eveassist.industry.internal.application.PlanEditing;
import com.eveassist.industry.internal.application.PlanEntry;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** The Facilities page and its forms, with each case they have to handle, because Thymeleaf checks nothing when the code compiles. */
@AutoConfigureMockMvc
class FacilityPagesIT extends IntegrationTest {

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

    private static ResultActions rendered(ResultActions result) throws Exception {
        return result.andExpect(content().string(not(containsString("??"))));
    }

    private MockHttpServletRequestBuilder form(String sub, MockHttpServletRequestBuilder request, String... fields) {
        for (int i = 0; i < fields.length; i += 2) {
            request.param(fields[i], fields[i + 1]);
        }
        return request.with(signedIn(sub)).with(csrf());
    }

    /** Adds a facility the way the form does, and answers its id. */
    private UUID added(String sub, String name, String index) throws Exception {
        mvc.perform(form(sub, post("/facilities"), "name", name, "structureType", "azbel", "structureMaterialPercent", "1",
                "structureTimePercent", "20", "structureCostPercent", "4", "systemCostIndexPercent", index))
                .andExpect(redirectedUrl("/facilities"));
        return jdbc.sql("SELECT public_id FROM facility WHERE name = :name ORDER BY id DESC LIMIT 1").param("name", name)
                .query(UUID.class).single();
    }

    private UUID projectUsing(String sub, UUID facility) throws Exception {
        var viewer = viewerFor(sub);
        var plan = planEditing.create(viewer, new CreatePlanCommand("Plan"));
        planEntry.addProduct(viewer, new AddProductCommand(plan, HAULER, 1));
        var location = mvc.perform(form(sub, post("/projects"), "plan", plan.value().toString())).andReturn().getResponse().getRedirectedUrl();
        var project = UUID.fromString(location.substring("/projects/".length()));
        var version = jdbc.sql("SELECT version FROM project WHERE public_id = :id").param("id", project).query(Long.class).single();
        mvc.perform(form(sub, post("/projects/" + project + "/config"), "expectedVersion", String.valueOf(version),
                "defaultFacility", facility.toString()));
        return project;
    }

    @Test
    void theListWithNoFacilitiesSaysSoAndOffersTheForm() throws Exception {
        rendered(mvc.perform(get("/facilities").with(signedIn(newSub()))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"no-facilities\"")))
                .andExpect(content().string(containsString("id=\"add-form\"")))
                .andExpect(content().string(containsString("data-fill-structure")))
                .andExpect(content().string(containsString("id=\"nav-facilities\"")));
    }

    @Test
    void anAddedFacilityIsListedWithItsStructureItsIndexAndHowOldTheIndexIsAndItsNameEscaped() throws Exception {
        var sub = newSub();
        var id = added(sub, "Azbel <b>one</b>", "2.16");

        rendered(mvc.perform(get("/facilities").with(signedIn(sub))))
                .andExpect(content().string(containsString("id=\"facility-" + id + "\"")))
                .andExpect(content().string(containsString("Azbel &lt;b&gt;one&lt;/b&gt;")))
                .andExpect(content().string(not(containsString("<b>one</b>"))))
                .andExpect(content().string(containsString("Cost index 2.16%")))
                .andExpect(content().string(containsString("typed today")))
                .andExpect(content().string(not(containsString("id=\"no-facilities\""))));
    }

    @Test
    void anEmptyPercentIsZeroAndAnEmptySccSurchargeIsFour() throws Exception {
        var sub = newSub();
        mvc.perform(form(sub, post("/facilities"), "name", "Bare", "systemCostIndexPercent", "1.5", "taxPercent", ""))
                .andExpect(redirectedUrl("/facilities"));

        var row = jdbc.sql("SELECT structure_time_percent, tax_percent, scc_surcharge_percent, structure_type FROM facility "
                        + "WHERE name = 'Bare' AND owner_account_id = :owner")
                .param("owner", viewerFor(sub).accountId().value())
                .query((rs, n) -> new Object[] {rs.getBigDecimal(1), rs.getBigDecimal(2), rs.getBigDecimal(3), rs.getString(4)}).single();
        assertThat((BigDecimal) row[0]).isEqualByComparingTo("0");
        assertThat((BigDecimal) row[1]).isEqualByComparingTo("0");
        assertThat((BigDecimal) row[2]).isEqualByComparingTo("4");
        assertThat(row[3]).isNull();
    }

    @Test
    void aFormOnAProjectsPageBringsTheUserBackToThatProject() throws Exception {
        var sub = newSub();
        var project = UUID.randomUUID();

        mvc.perform(form(sub, post("/facilities"), "name", "Quick", "systemCostIndexPercent", "2", "project", project.toString()))
                .andExpect(redirectedUrl("/projects/" + project + "#facilities"));
        // anything that is not a project id is ignored, so the redirect cannot be pointed anywhere else
        for (var notAnId : new String[] {"//example.com", "x y", "../admin", "1"}) {
            mvc.perform(form(sub, post("/facilities"), "name", "Other " + notAnId.length(), "systemCostIndexPercent", "2", "project", notAnId))
                    .andExpect(redirectedUrl("/facilities"));
        }
    }

    @Test
    void aChangedFacilityShowsItsNewValues() throws Exception {
        var sub = newSub();
        var id = added(sub, "Azbel", "2.16");

        mvc.perform(form(sub, post("/facilities/" + id), "name", "Renamed", "structureType", "", "systemCostIndexPercent", "3.5",
                "taxPercent", "1.5")).andExpect(redirectedUrl("/facilities"));

        rendered(mvc.perform(get("/facilities").with(signedIn(sub))))
                .andExpect(content().string(containsString("Renamed")))
                .andExpect(content().string(containsString("Cost index 3.5%")))
                .andExpect(content().string(containsString("value=\"1.5\"")));
    }

    @Test
    void aFacilityUsedByADraftSaysSoAndDeletingItTellsHowManyWereChanged() throws Exception {
        var sub = newSub();
        var id = added(sub, "Shared", "2");
        projectUsing(sub, id);

        rendered(mvc.perform(get("/facilities").with(signedIn(sub))))
                .andExpect(content().string(containsString("Used by 1 drafts")))
                .andExpect(content().string(containsString("1 draft projects use it")));

        mvc.perform(form(sub, post("/facilities/" + id + "/delete")))
                .andExpect(redirectedUrl("/facilities")).andExpect(flash().attribute("deletedDrafts", 1));
        rendered(mvc.perform(get("/facilities").with(signedIn(sub))))
                .andExpect(content().string(containsString("id=\"no-facilities\"")));
    }

    @Test
    void anotherAccountsFacilityIsNotFoundToChangeOrDelete() throws Exception {
        var owner = newSub();
        var id = added(owner, "Mine", "2");
        var stranger = newSub();

        mvc.perform(form(stranger, post("/facilities/" + id), "name", "Taken", "systemCostIndexPercent", "2")).andExpect(status().isNotFound());
        mvc.perform(form(stranger, post("/facilities/" + id + "/delete"))).andExpect(status().isNotFound());
        mvc.perform(form(owner, post("/facilities/" + UUID.randomUUID() + "/delete"))).andExpect(status().isNotFound());
        rendered(mvc.perform(get("/facilities").with(signedIn(stranger)))).andExpect(content().string(containsString("id=\"no-facilities\"")));
        rendered(mvc.perform(get("/facilities").with(signedIn(owner)))).andExpect(content().string(containsString("Mine")));
    }

    @Test
    void aRefusedValueComesBackAsAMessageAndSavesNothing() throws Exception {
        var sub = newSub();
        var id = added(sub, "Keep", "2");

        mvc.perform(form(sub, post("/facilities"), "name", "   ", "systemCostIndexPercent", "2"))
                .andExpect(redirectedUrl("/facilities")).andExpect(flash().attribute("facilityError", "name"));
        mvc.perform(form(sub, post("/facilities"), "name", "x".repeat(101), "systemCostIndexPercent", "2"))
                .andExpect(flash().attribute("facilityError", "name"));
        mvc.perform(form(sub, post("/facilities"), "name", "High", "systemCostIndexPercent", "2", "taxPercent", "101"))
                .andExpect(flash().attribute("facilityError", "invalid"));
        mvc.perform(form(sub, post("/facilities"), "name", "No index"))
                .andExpect(flash().attribute("facilityError", "invalid"));
        mvc.perform(form(sub, post("/facilities"), "name", "Text", "systemCostIndexPercent", "abc"))
                .andExpect(flash().attribute("facilityError", "invalid"));
        mvc.perform(form(sub, post("/facilities/" + id), "name", "Keep", "systemCostIndexPercent", "2", "structureTimePercent", "-1"))
                .andExpect(flash().attribute("facilityError", "invalid"));

        // the tests of a run share one database, so only this account's facilities are counted
        assertThat(jdbc.sql("SELECT count(*) FROM facility WHERE owner_account_id = :owner")
                .param("owner", viewerFor(sub).accountId().value()).query(Long.class).single()).isEqualTo(1L);
        // the facility still has the 20 it was added with: the refused change of it to -1 saved nothing
        assertThat(jdbc.sql("SELECT structure_time_percent FROM facility WHERE public_id = :id").param("id", id).query(BigDecimal.class).single())
                .isEqualByComparingTo("20");
    }

    @Test
    void aRefusedValueOnAProjectsPageComesBackToItAndOpensItsFacilitiesSection() throws Exception {
        var sub = newSub();
        var id = added(sub, "Mine", "2");
        var project = projectUsing(sub, id);

        mvc.perform(form(sub, post("/facilities"), "name", "  ", "systemCostIndexPercent", "2", "project", project.toString()))
                .andExpect(redirectedUrl("/projects/" + project + "#facilities"))
                .andExpect(flash().attribute("facilityError", "name"));
    }

    @Test
    void theProjectPageNamesTheStructureShowsHowOldTheIndexIsAndOffersTheForm() throws Exception {
        var sub = newSub();
        var id = added(sub, "Dodenvale Azbel", "2.16");
        var project = projectUsing(sub, id);

        rendered(mvc.perform(get("/projects/" + project).with(signedIn(sub))))
                .andExpect(content().string(containsString("Dodenvale Azbel")))
                .andExpect(content().string(containsString("typed today")))
                .andExpect(content().string(containsString("id=\"manage-facilities\"")))
                .andExpect(content().string(containsString("id=\"add-facility\"")))
                .andExpect(content().string(containsString("name=\"project\" value=\"" + project + "\"")));
    }
}
