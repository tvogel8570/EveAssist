package com.eveassist.industry.internal.project.web;

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
import com.eveassist.industry.internal.plan.application.AddProductCommand;
import com.eveassist.industry.internal.plan.application.CreatePlanCommand;
import com.eveassist.industry.internal.plan.application.PlanEditing;
import com.eveassist.industry.internal.plan.application.PlanEntry;
import com.eveassist.market.AdjustedPriceService;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The Products section of a project: what it shows, and adding, changing and removing a product and choosing whether a part is built or
 * bought. The project starts as one Test Hauler, which needs two Test Components (a part, with a blueprint of its own) and Tritanium.
 */
@AutoConfigureMockMvc
class ProjectProductsPagesIT extends IntegrationTest {

    private static final int SECOND_SHIP_TYPE = 1003;

    @Autowired
    MockMvc mvc;

    @Autowired
    PlanEditing planEditing;

    @Autowired
    PlanEntry planEntry;

    @Autowired
    AdjustedPriceService adjusted;

    @Autowired
    JdbcClient jdbc;

    private String sub;
    private UUID id;

    @BeforeEach
    void aProjectOfOneHauler() throws Exception {
        adjusted.refresh();
        sub = newSub();
        accounts.signedIn(sub, "Test Pilot");
        var viewer = viewerFor(sub);
        PlanId plan = planEditing.create(viewer, new CreatePlanCommand("Haulers"));
        planEntry.addProduct(viewer, new AddProductCommand(plan, HAULER, 1));
        var location = mvc.perform(post("/projects").param("plan", plan.value().toString()).with(signedIn(sub)).with(csrf()))
                .andReturn().getResponse().getRedirectedUrl();
        id = UUID.fromString(location.substring("/projects/".length()));
    }

    private RequestPostProcessor signedIn(String who) {
        accounts.signedIn(who, "Test Pilot");
        return oidcLogin().idToken(token -> token.subject(who)).authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private long version() {
        return jdbc.sql("SELECT version FROM project WHERE public_id = :id").param("id", id).query(Long.class).single();
    }

    private ResultActions page() throws Exception {
        return mvc.perform(get("/projects/" + id).with(signedIn(sub))).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("??"))));
    }

    private MockHttpServletRequestBuilder change(String path) {
        return post("/projects/" + id + path).param("expectedVersion", String.valueOf(version())).param("anchor", "products")
                .with(signedIn(sub)).with(csrf());
    }

    private List<String> products() {
        return jdbc.sql("SELECT x.type_id, x.quantity FROM project_product x JOIN project p ON p.id = x.project_id WHERE p.public_id = :id ORDER BY 1")
                .param("id", id).query((rs, n) -> rs.getInt(1) + " x " + rs.getLong(2)).list();
    }

    private List<Integer> bought() {
        return jdbc.sql("SELECT x.type_id FROM project_bought_part x JOIN project p ON p.id = x.project_id WHERE p.public_id = :id")
                .param("id", id).query(Integer.class).list();
    }

    @Test
    void theSectionShowsTheProductsAndThePartsWithWhatUsesThemAndHowManyTheLastCostingNeeded() throws Exception {
        var html = page().andReturn().getResponse().getContentAsString();

        assertThat(html).contains("id=\"products\"").contains("id=\"product-" + HAULER.value() + "\"")
                .contains("id=\"part-" + COMPONENT.value() + "\"")
                .containsPattern("id=\"part-for-" + COMPONENT.value() + "\"[^>]*>Test Hauler<")
                // one Hauler at ME 10 takes two Components
                .containsPattern("id=\"part-needed-" + COMPONENT.value() + "\"[^>]*>2<")
                .containsPattern("id=\"build-" + COMPONENT.value() + "\"[^>]*aria-pressed=\"true\"")
                .containsPattern("id=\"buy-" + COMPONENT.value() + "\"[^>]*aria-pressed=\"false\"");
    }

    @Test
    void theSearchOffersFormsThatAddToThisProjectAndAddingFetchesTheNewPrices() throws Exception {
        mvc.perform(get("/projects/" + id + "/product-search").param("q", "second").param("expectedVersion", "7").with(signedIn(sub)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("action=\"/projects/" + id + "/products\"")))
                // the version of the page the user is looking at goes with the choice, not the one on the server
                .andExpect(content().string(containsString("name=\"expectedVersion\" value=\"7\"")))
                .andExpect(content().string(containsString("value=\"" + SECOND_SHIP_TYPE + "\"")));

        mvc.perform(change("/products").param("typeId", String.valueOf(SECOND_SHIP_TYPE))).andExpect(redirectedUrl("/projects/" + id + "#products"));

        assertThat(products()).containsExactly("1001 x 1", SECOND_SHIP_TYPE + " x 1");
        page().andExpect(content().string(containsString("id=\"banner-stale\"")));
    }

    @Test
    void aQuantityCanBeChangedAndAProductRemoved() throws Exception {
        mvc.perform(change("/products").param("typeId", String.valueOf(SECOND_SHIP_TYPE)));

        mvc.perform(change("/products/1001/quantity").param("quantity", "5")).andExpect(redirectedUrl("/projects/" + id + "#products"));
        mvc.perform(change("/products/" + SECOND_SHIP_TYPE + "/remove")).andExpect(redirectedUrl("/projects/" + id + "#products"));

        assertThat(products()).containsExactly("1001 x 5");
    }

    @Test
    void aPartCanBeBoughtAndBuiltAgain() throws Exception {
        mvc.perform(change("/parts/" + COMPONENT.value()).param("buy", "true")).andExpect(redirectedUrl("/projects/" + id + "#products"));

        assertThat(bought()).containsExactly(COMPONENT.value());
        page().andExpect(content().string(org.hamcrest.Matchers.matchesPattern(
                "(?s).*id=\"buy-" + COMPONENT.value() + "\"[^>]*aria-pressed=\"true\".*")));

        mvc.perform(change("/parts/" + COMPONENT.value()).param("buy", "false"));

        assertThat(bought()).isEmpty();
    }

    @Test
    void whatIsRefusedSaysWhyAndChangesNothing() throws Exception {
        var before = version();

        mvc.perform(change("/products/1001/quantity").param("quantity", "0")).andExpect(flash().attribute("error", "quantity"));
        mvc.perform(change("/products/1001/quantity").param("quantity", "1000001")).andExpect(flash().attribute("error", "quantity"));
        mvc.perform(change("/products/1001/quantity").param("quantity", "many")).andExpect(flash().attribute("error", "invalid"));
        mvc.perform(change("/products").param("typeId", String.valueOf(TRITANIUM.value()))).andExpect(flash().attribute("error", "notBuildable"));
        mvc.perform(post("/projects/" + id + "/products/1001/quantity").param("expectedVersion", "999").param("quantity", "2")
                .with(signedIn(sub)).with(csrf())).andExpect(flash().attribute("error", "changed"));
        mvc.perform(change("/parts/" + TRITANIUM.value()).param("buy", "true")).andExpect(status().isNotFound());

        assertThat(version()).isEqualTo(before);
        assertThat(products()).containsExactly("1001 x 1");
    }

    @Test
    void anotherAccountFindsNoSuchProject() throws Exception {
        var other = newSub();

        for (var path : List.of("/products/1001/quantity", "/products/1001/remove", "/parts/" + COMPONENT.value(), "/products")) {
            mvc.perform(post("/projects/" + id + path).param("expectedVersion", String.valueOf(version())).param("quantity", "2")
                    .param("buy", "true").param("typeId", String.valueOf(SECOND_SHIP_TYPE)).with(signedIn(other)).with(csrf()))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(get("/projects/" + id + "/product-search").param("q", "second").param("expectedVersion", "1").with(signedIn(other)))
                .andExpect(status().isNotFound());
        assertThat(products()).containsExactly("1001 x 1");
    }

    @Test
    void aQuotedProjectShowsItsProductsAndOffersNoWayToChangeThem() throws Exception {
        var facility = UUID.randomUUID();
        jdbc.sql("INSERT INTO facility (public_id, owner_account_id, name) VALUES (:id, :owner, 'Azbel')")
                .param("id", facility).param("owner", viewerFor(sub).accountId().value()).update();
        mvc.perform(change("/config").param("defaultFacility", facility.toString()));
        mvc.perform(post("/projects/" + id + "/cost").with(signedIn(sub)).with(csrf()));
        mvc.perform(change("/quotes")).andExpect(flash().attributeCount(0));

        page().andExpect(content().string(containsString("id=\"product-" + HAULER.value() + "\"")))
                .andExpect(content().string(not(containsString("id=\"quantity-" + HAULER.value() + "\""))))
                .andExpect(content().string(not(containsString("id=\"remove-" + HAULER.value() + "\""))))
                .andExpect(content().string(not(containsString("id=\"product-search\""))))
                .andExpect(content().string(org.hamcrest.Matchers.matchesPattern(
                        "(?s).*id=\"buy-" + COMPONENT.value() + "\"[^>]*disabled.*")));
        mvc.perform(change("/products/1001/quantity").param("quantity", "2")).andExpect(flash().attribute("error", "locked"));
    }

    // ---- refreshing the prices ----

    private String storedSell(int type) {
        return jdbc.sql("SELECT x.sell FROM project_price x JOIN project p ON p.id = x.project_id WHERE p.public_id = :id AND x.type_id = :type")
                .param("id", id).param("type", type).query((rs, n) -> rs.getBigDecimal(1).stripTrailingZeros().toPlainString()).single();
    }

    @Test
    void refreshingThePricesTakesTheMarketsLeavesATypedOneAndMakesTheCostingOutOfDate() throws Exception {
        mvc.perform(change("/prices/" + COMPONENT.value()).param("price", "77"));
        mvc.perform(post("/projects/" + id + "/cost").with(signedIn(sub)).with(csrf()));
        page().andExpect(content().string(containsString("id=\"refresh-prices\"")));
        esi.sell(TRITANIUM.value(), "6.50");
        esi.sell(COMPONENT.value(), "90.00");

        mvc.perform(change("/prices/refresh")).andExpect(redirectedUrl("/projects/" + id + "#products"))
                .andExpect(flash().attributeCount(0));

        assertThat(storedSell(TRITANIUM.value())).isEqualTo("6.5");
        assertThat(storedSell(COMPONENT.value())).isEqualTo("77");
        page().andExpect(content().string(containsString("id=\"banner-stale\"")));
    }

    @Test
    void anItemEsiCannotAnswerForIsNamedAndKeepsItsPrice() throws Exception {
        var before = storedSell(TRITANIUM.value());
        esi.fail(TRITANIUM.value());
        mvc.perform(change("/prices/refresh")).andExpect(flash().attribute("pricesFailed", List.of("Tritanium")));

        assertThat(storedSell(TRITANIUM.value())).isEqualTo(before);
    }

    @Test
    void aQuotedProjectRefusesARefreshAndAnotherAccountFindsNone() throws Exception {
        mvc.perform(post("/projects/" + id + "/prices/refresh").with(signedIn(newSub())).with(csrf())).andExpect(status().isNotFound());

        var facility = UUID.randomUUID();
        jdbc.sql("INSERT INTO facility (public_id, owner_account_id, name) VALUES (:id, :owner, 'Azbel')")
                .param("id", facility).param("owner", viewerFor(sub).accountId().value()).update();
        mvc.perform(change("/config").param("defaultFacility", facility.toString()));
        mvc.perform(post("/projects/" + id + "/cost").with(signedIn(sub)).with(csrf()));
        mvc.perform(change("/quotes"));

        page().andExpect(content().string(not(containsString("id=\"refresh-prices\""))));
        mvc.perform(change("/prices/refresh")).andExpect(flash().attribute("error", "locked"));
    }
}

