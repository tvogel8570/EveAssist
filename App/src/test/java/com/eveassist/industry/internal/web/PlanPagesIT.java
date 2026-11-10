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
import com.eveassist.industry.internal.application.ChangeQuantityCommand;
import com.eveassist.industry.internal.application.CreatePlanCommand;
import com.eveassist.industry.internal.application.PlanEditing;
import com.eveassist.industry.internal.application.PlanEntry;
import com.eveassist.sde.TypeId;
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
    PlanEntry entry;

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
            entry.addProduct(viewer, new AddProductCommand(id, product, 1));
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

        // a newer SDE changes how a plan is costed, so the stored total is out of date
        jdbc.sql("UPDATE plan SET costed_sde_version = 'older-sde' WHERE public_id = :id")
                .param("id", id.value()).update();

        rendered(mvc.perform(get("/plans").with(session)))
                .andExpect(content().string(containsString("Out of date")))
                .andExpect(content().string(containsString("700 ISK")));

        rendered(mvc.perform(get("/plans/" + id.value()).with(session)))
                .andExpect(content().string(containsString("700 ISK")));
        rendered(mvc.perform(get("/plans").with(session)))
                .andExpect(content().string(not(containsString("Out of date"))));
    }

    @Test
    void aNewerMarketPriceChangesNothingOnAPlanUntilTheUserRefreshes() throws Exception {
        var sub = newSub();
        var id = planWith(sub, HAULER);
        var session = signedIn(sub);

        esi.sell(34, "6.00");

        rendered(mvc.perform(get("/plans/" + id.value()).with(session)))
                .andExpect(content().string(containsString("700 ISK")));
        rendered(mvc.perform(get("/plans").with(session)))
                .andExpect(content().string(not(containsString("Out of date"))))
                .andExpect(content().string(containsString("700 ISK")));

        rendered(mvc.perform(post("/plans/{id}/prices/refresh", id.value()).with(session).with(csrf())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"plan-total-value\"")))
                .andExpect(content().string(containsString("820 ISK")));
        rendered(mvc.perform(get("/plans/" + id.value()).with(session)))
                .andExpect(content().string(containsString("820 ISK")));
    }

    @Test
    void refreshingOnePlanLeavesAnotherPlanAsQuoted() throws Exception {
        var sub = newSub();
        var quoted = planWith(sub, HAULER);
        var refreshed = planWith(sub, HAULER);
        var session = signedIn(sub);
        esi.sell(34, "6.00");

        mvc.perform(post("/plans/{id}/prices/refresh", refreshed.value()).with(session).with(csrf()))
                .andExpect(status().isOk());

        rendered(mvc.perform(get("/plans/" + quoted.value()).with(session)))
                .andExpect(content().string(containsString("700 ISK")));
        rendered(mvc.perform(get("/plans/" + refreshed.value()).with(session)))
                .andExpect(content().string(containsString("820 ISK")));
    }

    @Test
    void refreshingKeepsThePriceOfAnItemEsiCouldNotAnswerAndSaysSo() throws Exception {
        var sub = newSub();
        var id = planWith(sub, HAULER);
        var session = signedIn(sub);
        esi.sell(34, "6.00");
        esi.fail(35);

        var body = rendered(mvc.perform(post("/plans/{id}/prices/refresh", id.value()).with(session).with(csrf())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("id=\"price-warning\"", "Could not refresh", "Pilot&#39;s Mineral");
        // Tritanium rose by 1 on 120 units; the failed item kept its price of 10
        rendered(mvc.perform(get("/plans/" + id.value()).with(session)))
                .andExpect(content().string(containsString("820 ISK")));
    }

    @Test
    void addingAProductFetchesOnlyTheItemsThePlanHasNoPriceFor() throws Exception {
        var sub = newSub();
        var id = planWith(sub, HAULER);
        var before = esi.asked().size();
        esi.sell(34, "6.00");

        entry.addProduct(viewerFor(sub), new AddProductCommand(id, SECOND_SHIP, 1));

        var asked = esi.asked().subList(before, esi.asked().size());
        assertThat(asked).as("Tritanium is already priced, so it is not asked about again")
                .doesNotContain(34).contains(1003);
        rendered(mvc.perform(get("/plans/" + id.value()).with(signedIn(sub))))
                .andExpect(content().string(not(containsString("820 ISK"))));
    }

    @Test
    void anAddedProductWhoseItemsEsiCannotAnswerIsLeftUnpriced() throws Exception {
        var sub = newSub();
        var id = planWith(sub);
        esi.fail(34);

        entry.addProduct(viewerFor(sub), new AddProductCommand(id, HAULER, 1));

        rendered(mvc.perform(get("/plans/" + id.value()).with(signedIn(sub))))
                .andExpect(content().string(containsString("id=\"plan-unpriced\"")));
    }

    @Test
    void refreshingAnotherAccountsPlanIsNotFound() throws Exception {
        var foreign = planWith(newSub(), HAULER);

        mvc.perform(post("/plans/{id}/prices/refresh", foreign.value()).with(signedIn(newSub())).with(csrf()))
                .andExpect(status().isNotFound());
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
    void anItemThatCannotBeBuiltCannotBeAddedAsAProduct() throws Exception {
        var sub = newSub();
        var id = planWith(sub);

        for (var bad : new String[] {"1", "-5", "34", "999999"}) {
            mvc.perform(post("/plans/{id}/products", id.value()).param("typeId", bad)
                            .with(signedIn(sub)).with(csrf()))
                    .andExpect(status().isUnprocessableContent());
        }

        assertThat(esi.asked()).as("ESI is not asked about an item that cannot be a product").isEmpty();
        rendered(mvc.perform(get("/plans/" + id.value()).with(signedIn(sub))))
                .andExpect(content().string(containsString("id=\"no-products\"")));
    }

    @Test
    void aBlankOrOverLongPlanNameIsRefusedWhateverThePageAllowed() throws Exception {
        var sub = newSub();

        for (var bad : new String[] {" ", "   ", "x".repeat(101)}) {
            mvc.perform(post("/plans").param("name", bad).with(signedIn(sub)).with(csrf()))
                    .andExpect(status().isUnprocessableContent());
        }
        mvc.perform(post("/plans").param("name", "  Padded  ").with(signedIn(sub)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        rendered(mvc.perform(get("/plans").with(signedIn(sub))))
                .andExpect(content().string(containsString(">Padded<")));
    }

    @Test
    void aPartHiddenBelowABoughtPartCannotBeChosen() throws Exception {
        var sub = newSub();
        var id = planWith(sub, new TypeId(1005));

        mvc.perform(post("/plans/{id}/parts/{type}/buy", id.value(), 1006)
                        .param("expectedVersion", "1").with(signedIn(sub)).with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(post("/plans/{id}/parts/{type}/buy", id.value(), 1007)
                        .param("expectedVersion", "2").with(signedIn(sub)).with(csrf()))
                .andExpect(status().isNotFound());

        mvc.perform(post("/plans/{id}/parts/{type}/build", id.value(), 1006)
                        .param("expectedVersion", "2").with(signedIn(sub)).with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(post("/plans/{id}/parts/{type}/buy", id.value(), 1007)
                        .param("expectedVersion", "3").with(signedIn(sub)).with(csrf()))
                .andExpect(status().isOk());
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

    @Test
    void thePlanPageHasANewButtonThatAsksForANameAndCreatesAPlan() throws Exception {
        var sub = newSub();
        var id = planWith(sub, HAULER);
        var session = signedIn(sub);

        var page = rendered(mvc.perform(get("/plans/" + id.value()).with(session)))
                .andReturn().getResponse().getContentAsString();

        // a menu with a name box that posts to the same create action as the list, with the CSRF token Thymeleaf adds
        assertThat(page).contains("id=\"new-plan-menu\"").contains("id=\"new-plan-toggle\"")
                .contains("action=\"/plans\"").contains("name=\"name\"").contains("autocomplete=\"off\"")
                .contains("data-focus-on-open").contains("name=\"_csrf\"");
        mvc.perform(post("/plans").param("name", "From the plan page").with(session).with(csrf()))
                .andExpect(status().is3xxRedirection());
        rendered(mvc.perform(get("/plans").with(session))).andExpect(content().string(containsString("From the plan page")));
    }

    // ---- removing a product, and putting it back

    private String removeBody(String sub, PlanId id, int type, long version) throws Exception {
        return rendered(mvc.perform(post("/plans/{id}/products/{type}/remove", id.value(), type)
                        .param("expectedVersion", String.valueOf(version)).with(signedIn(sub)).with(csrf())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private long storedProducts(PlanId id) {
        return jdbc.sql("SELECT count(*) FROM plan_product pp JOIN plan p ON p.id = pp.plan_id WHERE p.public_id = :id")
                .param("id", id.value()).query(Long.class).single();
    }

    @Test
    void removingAProductRedrawsThePlanCostsTheRestAndOffersUndo() throws Exception {
        var sub = newSub();
        var id = planWith(sub, HAULER);

        var body = removeBody(sub, id, 1001, 1);

        // the products, the totals and the version are replaced, and what was removed can be put back
        assertThat(body).contains("id=\"products\"", "id=\"no-products\"", "id=\"plan-total\"", "id=\"plan-version\"",
                "value=\"2\"", "id=\"undo-bar\"", "Removed Test Hauler (1 unit).", "id=\"undo-remove\"",
                "/plans/" + id.value() + "/products/1001/restore", "name=\"quantity\"");
        assertThat(body).doesNotContain("id=\"quantity-1001\"", "<html");
        assertThat(storedProducts(id)).isZero();
    }

    @Test
    void aRemovedProductIsOfferedBackWithTheUnitsItHad() throws Exception {
        var sub = newSub();
        var id = planWith(sub, HAULER);
        editing.changeQuantity(viewerFor(sub), new ChangeQuantityCommand(id, HAULER, 3, 1));

        var body = removeBody(sub, id, 1001, 2);

        assertThat(body).contains("Removed Test Hauler (3 units).", "value=\"3\"");
    }

    @Test
    void undoPutsTheProductBackWithItsUnitsAndTheQuoteItHad() throws Exception {
        var sub = newSub();
        var id = planWith(sub, HAULER);
        var viewer = viewerFor(sub);
        editing.changeQuantity(viewer, new ChangeQuantityCommand(id, HAULER, 3, 1));
        var before = entry.open(viewer, id).totalCost();
        removeBody(sub, id, 1001, 2);

        var body = rendered(mvc.perform(post("/plans/{id}/products/{type}/restore", id.value(), 1001)
                        .param("quantity", "3").with(signedIn(sub)).with(csrf())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(body).contains("id=\"quantity-1001\"", "value=\"3\"", "id=\"undo-bar\"");
        assertThat(body).doesNotContain("Removed Test Hauler", "id=\"no-products\"");
        var after = entry.open(viewer, id);
        assertThat(after.products()).singleElement().satisfies(line -> assertThat(line.quantity()).isEqualTo(3));
        // the plan kept its prices, so it is quoted as before and is not left unpriced
        assertThat(after.totalCost()).isEqualByComparingTo(before);
        assertThat(after.unpricedLines()).isZero();
    }

    @Test
    void removingFromAPlanThatChangedOrDoesNotHaveTheProductOrIsAnotherAccountsIsRefused() throws Exception {
        var sub = newSub();
        var id = planWith(sub, HAULER);

        mvc.perform(post("/plans/{id}/products/{type}/remove", id.value(), 1001)
                        .param("expectedVersion", "0").with(signedIn(sub)).with(csrf()))
                .andExpect(status().isConflict());
        mvc.perform(post("/plans/{id}/products/{type}/remove", id.value(), 34)
                        .param("expectedVersion", "1").with(signedIn(sub)).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/plans/{id}/products/{type}/remove", id.value(), 1001)
                        .param("expectedVersion", "1").with(signedIn(newSub())).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/plans/{id}/products/{type}/remove", id.value(), 1001).param("expectedVersion", "1")
                        .with(signedIn(sub)))
                .andExpect(status().isForbidden());
        // nothing was removed by any of them
        assertThat(storedProducts(id)).isEqualTo(1);
    }

    @Test
    void theEditorOffersARemoveButtonForEachProductAndAnEmptyUndoBar() throws Exception {
        var sub = newSub();
        var id = planWith(sub, HAULER);

        var page = rendered(mvc.perform(get("/plans/" + id.value()).with(signedIn(sub))))
                .andReturn().getResponse().getContentAsString();

        assertThat(page).contains("id=\"remove-1001\"", "aria-label=\"Remove Test Hauler\"",
                "/plans/" + id.value() + "/products/1001/remove", "id=\"undo-bar\"", "data-plan-quantity",
                "data-saved=\"1\"");
        assertThat(page).doesNotContain("Removed Test Hauler");
    }
}
