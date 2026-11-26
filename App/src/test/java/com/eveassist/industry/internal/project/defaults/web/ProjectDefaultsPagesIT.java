package com.eveassist.industry.internal.project.defaults.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
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
import com.eveassist.industry.FacilityId;
import com.eveassist.industry.internal.costing.domain.SplitStrategy;
import com.eveassist.industry.internal.project.defaults.application.ProjectDefaults;
import com.eveassist.industry.internal.project.domain.PriceSide;
import com.eveassist.industry.internal.project.domain.ProjectConfig;
import com.eveassist.industry.internal.project.domain.ProjectSettings.QuoteMode;
import com.eveassist.industry.internal.project.web.ConfigForm;
import com.eveassist.sde.TypeId;
import java.util.List;
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

/** The project defaults page: what it shows, what saving and resetting do, what it refuses, and the ways to it and back. */
@AutoConfigureMockMvc
class ProjectDefaultsPagesIT extends IntegrationTest {

    private static final TypeId NANITE = new TypeId(11442);
    private static final TypeId MECHANICAL = new TypeId(11452);

    @Autowired
    MockMvc mvc;

    @Autowired
    ProjectDefaults defaults;

    @Autowired
    JdbcClient jdbc;

    private RequestPostProcessor signedIn(String sub) {
        accounts.signedIn(sub, "Test Pilot");
        return oidcLogin().idToken(token -> token.subject(sub)).authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private ResultActions page(String sub) throws Exception {
        return mvc.perform(get("/project-defaults").with(signedIn(sub))).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("??"))));
    }

    private UUID facilityOf(String sub) {
        var id = UUID.randomUUID();
        jdbc.sql("INSERT INTO facility (public_id, owner_account_id, name) VALUES (:id, :owner, 'Test Azbel')")
                .param("id", id).param("owner", viewerFor(sub).accountId().value()).update();
        return id;
    }

    /** The page's form as a browser sends it: every field, each time. */
    private MockHttpServletRequestBuilder form(String sub, String facility, String longestDays, String margin, String nanite) {
        return post("/project-defaults").with(signedIn(sub)).with(csrf())
                .param("priceSide", "SPLIT").param("defaultFacility", facility).param("iskPerCubicMeter", "300").param("contractFee", "25000")
                .param("iskPerHour", "500000").param("splitStrategy", "COPIES_FIRST").param("maxJobDays", longestDays).param("mode", "MARKET")
                .param("marginPercent", margin).param("premiumPercent", "-5").param("quoteStep", "10000").param("industry", "5")
                .param("advancedIndustry", "4").param("implantTimePercent", "4")
                .param("skill-" + NANITE.value(), nanite).param("skill-" + MECHANICAL.value(), "0").param("skill-999999", "5");
    }

    private ProjectConfig stored(String sub) {
        return defaults.of(viewerFor(sub));
    }

    @Test
    void beforeAnythingIsSavedItShowsTheBuiltInValuesAndEverySkillAtZeroUnderItsGroup() throws Exception {
        var sub = newSub();

        page(sub).andExpect(content().string(containsString("id=\"never-saved\"")))
                .andExpect(content().string(not(containsString("id=\"reset-defaults\""))))
                .andExpect(content().string(containsString("name=\"contractFee\" value=\"10000\"")))
                .andExpect(content().string(containsString("Nanite Engineering (1%)")))
                .andExpect(content().string(containsString("Mechanical Engineering (2%)")))
                .andExpect(content().string(containsString("0 of 2 set")))
                .andExpect(content().string(containsString("id=\"set-all-0\"")))
                // the section of the skills is closed until the user opens it
                .andExpect(content().string(matchesPattern("(?s).*<details id=\"default-skills\" data-section=\"default-skills\" class=\"[^\"]*\">.*")));
    }

    @Test
    void savingStoresEveryFieldAndTheSkillsAboveZeroThatThePageShows() throws Exception {
        var sub = newSub();
        var facility = facilityOf(sub);

        mvc.perform(form(sub, facility.toString(), "2.5", "12.5", "3")).andExpect(redirectedUrl("/project-defaults"))
                .andExpect(flash().attribute("saved", "saved"));

        var saved = stored(sub);
        assertThat(saved.priceSide()).isEqualTo(PriceSide.SPLIT);
        assertThat(saved.defaultFacility()).isEqualTo(new FacilityId(facility));
        assertThat(saved.settings().iskPerCubicMeter()).isEqualByComparingTo("300");
        assertThat(saved.settings().contractFee()).isEqualByComparingTo("25000");
        assertThat(saved.settings().iskPerHour()).isEqualByComparingTo("500000");
        assertThat(saved.settings().mode()).isEqualTo(QuoteMode.MARKET);
        assertThat(saved.settings().marginPercent()).isEqualByComparingTo("12.5");
        assertThat(saved.settings().premiumPercent()).isEqualByComparingTo("-5");
        assertThat(saved.settings().quoteStep()).isEqualByComparingTo("10000");
        assertThat(saved.defaultPolicy().strategy()).isEqualTo(SplitStrategy.COPIES_FIRST);
        assertThat(saved.defaultPolicy().maxJobSeconds()).isEqualTo(216_000L);
        assertThat(saved.builder().industry()).isEqualTo(5);
        assertThat(saved.builder().advancedIndustry()).isEqualTo(4);
        assertThat(saved.builder().implantTimePercent()).isEqualByComparingTo("4");
        // not the skill at 0, and not one the page does not show
        assertThat(saved.builder().skillLevels()).containsOnly(java.util.Map.entry(NANITE, 3));

        page(sub).andExpect(content().string(not(containsString("id=\"never-saved\""))))
                .andExpect(content().string(containsString("id=\"reset-defaults\"")))
                .andExpect(content().string(containsString("name=\"maxJobDays\" value=\"2.5\"")))
                .andExpect(content().string(containsString("1 of 2 set")));
    }

    @Test
    void anEmptyFacilityAndAnEmptyLongestJobClearThemAndASkillCanGoBackToZero() throws Exception {
        var sub = newSub();
        mvc.perform(form(sub, facilityOf(sub).toString(), "7", "20", "3"));

        mvc.perform(form(sub, "", "", "20", "0")).andExpect(flash().attribute("saved", "saved"));

        assertThat(stored(sub).defaultFacility()).isNull();
        assertThat(stored(sub).defaultPolicy().maxJobSeconds()).isNull();
        assertThat(stored(sub).builder().skillLevels()).isEmpty();
    }

    @Test
    void aLongestJobComesBackFromTheFormAsTheSameSeconds() {
        // seconds that are no round part of a day: shown in days and typed back, they must not drift
        for (var seconds : List.of(1L, 100_000L, 3_599L, 86_399L, 2_592_000L)) {
            var days = ConfigForm.days(seconds);
            var form = new ConfigForm(null, null, null, null, null, null, null, null, null, null, null, null, null, null, days);
            assertThat(form.applyTo(ProjectConfig.defaults(), java.util.Map.of()).defaultPolicy().maxJobSeconds()).as(days).isEqualTo(seconds);
        }
    }

    @Test
    void aRefusedValueSavesNothingAndSaysSo() throws Exception {
        var sub = newSub();
        var foreign = facilityOf(newSub());

        for (var days : List.of("0", "-1", "abc", "1e9")) {
            mvc.perform(form(sub, "", days, "20", "3")).andExpect(redirectedUrl("/project-defaults")).andExpect(flash().attribute("error", "invalid"));
        }
        mvc.perform(form(sub, "", "", "100", "3")).andExpect(flash().attribute("error", "invalid"));
        mvc.perform(form(sub, "", "", "20", "6")).andExpect(flash().attribute("error", "invalid"));
        mvc.perform(form(sub, foreign.toString(), "", "20", "3")).andExpect(flash().attribute("error", "facility"));

        page(sub).andExpect(content().string(containsString("id=\"never-saved\"")));
    }

    @Test
    void resettingReturnsToTheBuiltInValues() throws Exception {
        var sub = newSub();
        mvc.perform(form(sub, "", "", "12", "3"));

        mvc.perform(post("/project-defaults/reset").with(signedIn(sub)).with(csrf())).andExpect(redirectedUrl("/project-defaults"))
                .andExpect(flash().attribute("saved", "reset"));

        page(sub).andExpect(content().string(containsString("id=\"never-saved\"")));
        assertThat(stored(sub).settings().marginPercent()).isEqualByComparingTo("20");
    }

    @Test
    void aFacilityAddedFromTheDefaultsReturnsToThem() throws Exception {
        var sub = newSub();
        page(sub).andExpect(content().string(containsString("data-goto=\"/facilities?from=defaults\"")));
        mvc.perform(get("/facilities?from=defaults").with(signedIn(sub))).andExpect(content().string(containsString("id=\"back-to-defaults\"")))
                .andExpect(content().string(containsString("name=\"from\" value=\"defaults\"")));
        mvc.perform(get("/facilities?from=elsewhere").with(signedIn(sub))).andExpect(content().string(not(containsString("id=\"back-to-defaults\""))));

        mvc.perform(post("/facilities").with(signedIn(sub)).with(csrf()).param("from", "defaults").param("name", "New Azbel")
                        .param("structureMaterialPercent", "1").param("rigMaterialPercent", "0").param("structureTimePercent", "20")
                        .param("rigTimePercent", "0").param("structureCostPercent", "4").param("systemCostIndexPercent", "2")
                        .param("taxPercent", "0").param("sccSurchargePercent", "4"))
                .andExpect(redirectedUrl("/project-defaults"));

        page(sub).andExpect(content().string(containsString("New Azbel")));
    }

    @Test
    void theProjectsListAndTheAccountPageLinkToIt() throws Exception {
        var sub = newSub();

        mvc.perform(get("/projects").with(signedIn(sub))).andExpect(content().string(containsString("id=\"project-defaults-link\"")));
        mvc.perform(get("/account").with(signedIn(sub))).andExpect(content().string(containsString("id=\"account-project-defaults\"")));
    }

    @Test
    void aVisitorIsSentToSignIn() throws Exception {
        mvc.perform(get("/project-defaults")).andExpect(status().is3xxRedirection());
    }
}
