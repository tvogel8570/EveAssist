package com.eveassist.industry.internal.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eveassist.IntegrationTest;
import com.eveassist.industry.PlanId;
import com.eveassist.industry.internal.application.AddProductCommand;
import com.eveassist.industry.internal.application.CreatePlanCommand;
import com.eveassist.industry.internal.application.PlanEditing;
import com.eveassist.market.JitaPrice;
import com.eveassist.sde.TypeId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Renders every template with each case it has to handle, because Thymeleaf checks nothing when the code compiles. */
@AutoConfigureMockMvc
class PlanPagesIT extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    PlanEditing editing;

    @Autowired
    JdbcClient jdbc;

    private RequestPostProcessor signedIn(String sub) {
        accounts.signedIn(sub, "Test Pilot");
        return oidcLogin().idToken(token -> token.subject(sub)).authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private PlanId planWith(String sub, TypeId... products) {
        var viewer = viewerFor(sub);
        var id = editing.create(viewer, new CreatePlanCommand("My <i>plan</i>"));
        for (var product : products) {
            editing.addProduct(viewer, new AddProductCommand(id, product, 1));
        }
        return id;
    }

    private static ResultActions rendered(ResultActions result) throws Exception {
        return result.andExpect(content().string(not(containsString("??"))));
    }

    @Test
    void listWithNoPlans() throws Exception {
        rendered(mvc.perform(get("/plans").with(signedIn(newSub()))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"no-plans\"")));
    }

    @Test
    void listWithACostedAndAnUncostedPlanEscapesTheName() throws Exception {
        var sub = newSub();
        planWith(sub, HAULER);
        planWith(sub);

        rendered(mvc.perform(get("/plans").with(signedIn(sub))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Not costed")))
                .andExpect(content().string(containsString("700 ISK")))
                .andExpect(content().string(containsString("My &lt;i&gt;plan&lt;/i&gt;")));
    }

    @Test
    void listMarksAStaleTotalAndOpeningThePlanReplacesIt() throws Exception {
        var sub = newSub();
        var id = planWith(sub, HAULER);
        var session = signedIn(sub);
        rendered(mvc.perform(get("/plans").with(session)))
                .andExpect(content().string(not(containsString("Out of date"))));

        market.store(List.of(new JitaPrice(TRITANIUM, new BigDecimal("6.00"), Instant.now().minusSeconds(1))));
        jdbc.sql("UPDATE plan SET costed_at = now() - interval '1 hour' WHERE public_id = :id")
                .param("id", id.value()).update();

        rendered(mvc.perform(get("/plans").with(session)))
                .andExpect(content().string(containsString("Out of date")))
                .andExpect(content().string(containsString("700 ISK")));

        rendered(mvc.perform(get("/plans/" + id.value()).with(session)))
                .andExpect(content().string(containsString("820 ISK")));
        rendered(mvc.perform(get("/plans").with(session)))
                .andExpect(content().string(not(containsString("Out of date"))))
                .andExpect(content().string(containsString("820 ISK")));
    }

    @Test
    void openingAnEmptyPlanLeavesItNotCosted() throws Exception {
        var sub = newSub();
        var id = planWith(sub);

        mvc.perform(get("/plans/" + id.value()).with(signedIn(sub)));

        rendered(mvc.perform(get("/plans").with(signedIn(sub))))
                .andExpect(content().string(containsString("Not costed")));
    }

    @Test
    void editorWithNoProducts() throws Exception {
        var sub = newSub();
        var id = planWith(sub);

        rendered(mvc.perform(get("/plans/" + id.value()).with(signedIn(sub))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"no-products\"")));
    }

    @Test
    void editorShowsLinesAndEscapesNames() throws Exception {
        var sub = newSub();
        var id = planWith(sub, HAULER, SECOND_SHIP);

        rendered(mvc.perform(get("/plans/" + id.value()).with(signedIn(sub))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"line-34\"")))
                .andExpect(content().string(containsString("Second &lt;b&gt;Ship&lt;/b&gt;")))
                .andExpect(content().string(containsString("Pilot&#39;s Mineral")))
                .andExpect(content().string(containsString("id=\"plan-unpriced\"")))
                .andExpect(content().string(containsString("No price")));
    }

    @Test
    void editReturnsOnlyChangedLinesAndTheTotalOutOfBand() throws Exception {
        var sub = newSub();
        var id = planWith(sub, HAULER, new TypeId(1004));

        var body = rendered(mvc.perform(post("/plans/{id}/products/{type}/quantity", id.value(), 1001)
                        .param("quantity", "2").param("expectedVersion", "2")
                        .with(signedIn(sub)).with(csrf())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("id=\"plan-total\"", "hx-swap-oob", "id=\"line-1002\"", "id=\"line-34\"",
                "id=\"build-1002\"", "id=\"plan-version\"", "value=\"3\"", "id=\"saved-1001\"");
        assertThat(body).doesNotContain("id=\"line-1001\"", "id=\"line-1004\"", "<html");
        assertThat(body.split("id=\"line-1002\"", -1)).as("each changed row appears once").hasSize(2);
    }

    @Test
    void buyingAPartRedrawsThePlanWithoutWhatWasBelowIt() throws Exception {
        var sub = newSub();
        var id = planWith(sub, SECOND_SHIP);

        var body = rendered(mvc.perform(post("/plans/{id}/parts/{type}/buy", id.value(), 1002)
                        .param("expectedVersion", "1").with(signedIn(sub)).with(csrf())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("id=\"parts-section\"", "id=\"materials-section\"", "id=\"line-1002\"",
                "id=\"plan-unpriced\"");
        assertThat(body).doesNotContain("id=\"line-34\"", "<html");
    }

    @Test
    void anItemThatIsNotAPartCannotBeChosen() throws Exception {
        var sub = newSub();
        var id = planWith(sub, HAULER);

        mvc.perform(post("/plans/{id}/parts/{type}/buy", id.value(), 34)
                        .param("expectedVersion", "1").with(signedIn(sub)).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/plans/{id}/parts/{type}/buy", id.value(), 1001)
                        .param("expectedVersion", "1").with(signedIn(sub)).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void editWithAnOldVersionIsAConflict() throws Exception {
        var sub = newSub();
        var id = planWith(sub, HAULER);

        mvc.perform(post("/plans/{id}/products/{type}/quantity", id.value(), 1001)
                        .param("quantity", "2").param("expectedVersion", "0")
                        .with(signedIn(sub)).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void editWithABadQuantityIsRefused() throws Exception {
        var sub = newSub();
        var id = planWith(sub, HAULER);

        mvc.perform(post("/plans/{id}/products/{type}/quantity", id.value(), 1001)
                        .param("quantity", "0").param("expectedVersion", "1")
                        .with(signedIn(sub)).with(csrf()))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void searchWithMatchesAndWithout() throws Exception {
        var sub = newSub();
        var id = planWith(sub);

        rendered(mvc.perform(get("/plans/{id}/product-search", id.value()).param("q", "haul").with(signedIn(sub))))
                .andExpect(content().string(containsString("Test Hauler")));
        rendered(mvc.perform(get("/plans/{id}/product-search", id.value()).param("q", "zzz").with(signedIn(sub))))
                .andExpect(content().string(containsString("No items found")));
    }

    @Test
    void anotherAccountsPlanAndAMissingPlanGiveTheSamePage() throws Exception {
        var other = newSub();
        var foreign = planWith(newSub(), HAULER);

        var seenForeign = rendered(mvc.perform(get("/plans/" + foreign.value()).with(signedIn(other))))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        var seenMissing = rendered(mvc.perform(get("/plans/" + UUID.randomUUID()).with(signedIn(other))))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();

        assertThat(withoutCsrf(seenForeign)).isEqualTo(withoutCsrf(seenMissing));
    }

    private static String withoutCsrf(String html) {
        return html.replaceAll("(name=\"_csrf\" (content|value)=\")[^\"]*", "$1");
    }

    @Test
    void anAdminCannotUsePlans() throws Exception {
        var sub = newSub();
        accounts.signedIn(sub, "Admin");
        mvc.perform(get("/plans").with(oidcLogin().idToken(token -> token.subject(sub))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void aSignedOutHtmxRequestIsToldToSignIn() throws Exception {
        mvc.perform(get("/plans").header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Redirect", "/oauth2/authorization/keycloak"));
        mvc.perform(post("/plans/x/products/1/quantity").header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Redirect", "/oauth2/authorization/keycloak"));
    }

    @Test
    void aSignedOutPageRequestGoesToSignIn() throws Exception {
        mvc.perform(get("/plans"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("/oauth2/authorization/keycloak")));
    }
}
