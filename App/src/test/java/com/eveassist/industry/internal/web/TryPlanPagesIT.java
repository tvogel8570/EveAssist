package com.eveassist.industry.internal.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eveassist.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** A plan without an account, with nobody signed in: what a visitor can do and what the server keeps. */
@AutoConfigureMockMvc
class TryPlanPagesIT extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    /** Prices stored by other tests would hide whether this one fetched anything. */
    @BeforeEach
    void noStoredPrices() {
        jdbc.sql("DELETE FROM jita_order").update();
        jdbc.sql("DELETE FROM jita_fetch").update();
    }

    private ResultActions add(String state, int type) throws Exception {
        return mvc.perform(post("/try/products").param("state", state).param("typeId", String.valueOf(type)));
    }

    @Test
    void theEmptyPageIsOpenToEveryoneAndOffersNoRefreshAndNoSave() throws Exception {
        mvc.perform(get("/try"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Try a plan")))
                .andExpect(content().string(containsString("We do not keep your plan")))
                .andExpect(content().string(containsString("All prices are Jita 4-4 only")))
                .andExpect(content().string(containsString("id=\"try-state\"")))
                .andExpect(content().string(containsString("id=\"try-sign-in\"")))
                .andExpect(content().string(not(containsString("refresh-prices"))))
                .andExpect(content().string(not(containsString("new-plan"))));
    }

    @Test
    void theLandingPageOffersTheTryPageToAnyoneWithoutThePlansLink() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"try-plan\"")))
                .andExpect(content().string(containsString("id=\"try-plan-button\"")))
                .andExpect(content().string(containsString("Try a plan without signing in")));

        // signed in but without the USER role: no Plans link in the top bar, so the card is still the way in
        mvc.perform(get("/").with(oidcLogin().idToken(token -> token.subject("no-role-sub"))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"try-plan-button\"")));

        accounts.signedIn("landing-sub", "Test Pilot");
        mvc.perform(get("/").with(oidcLogin().idToken(token -> token.subject("landing-sub"))
                        .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("id=\"try-plan\""))))
                .andExpect(content().string(not(containsString("id=\"try-plan-button\""))));
    }

    @Test
    void theSignedInPlansStillNeedASignIn() throws Exception {
        mvc.perform(get("/plans")).andExpect(status().is3xxRedirection());
    }

    @Test
    void addingAProductNeedsNoTokenCostsItAndGivesBackTheState() throws Exception {
        add("", HAULER.value())
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("630 ISK")))
                .andExpect(content().string(containsString("value=\"p=1001:1;b=\"")))
                .andExpect(content().string(containsString("Test Hauler")))
                .andExpect(content().string(containsString("id=\"run-1001\"")));
    }

    @Test
    void priceIsFetchedOnlyForAnItemWithNoStoredPriceAndNeverRefreshed() throws Exception {
        add("", HAULER.value()).andExpect(content().string(containsString("630 ISK")));
        int asked = esi.asked().size();
        assertThat(asked).isPositive();

        esi.sell(34, "99.00");
        mvc.perform(post("/try/render").param("state", "p=1001:1;b="))
                .andExpect(content().string(containsString("630 ISK")));

        assertThat(esi.asked()).hasSize(asked);
    }

    @Test
    void anItemThatCannotBePricedIsListedAndLeftOutOfTheTotal() throws Exception {
        esi.fail(TRITANIUM.value());

        add("", HAULER.value())
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"try-unpriced\"")))
                .andExpect(content().string(containsString("Tritanium")));
    }

    @Test
    void changingUnitsKeepsTheProductsAndGivesBackTheNewState() throws Exception {
        add("", HAULER.value());

        mvc.perform(post("/try/products/1001/quantity").param("state", "p=1001:1;b=").param("quantity", "2"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("1,260 ISK")))
                .andExpect(content().string(containsString("value=\"p=1001:2;b=\"")))
                .andExpect(content().string(not(containsString("id=\"products\""))));
    }

    @Test
    void unitsThePlanCannotHoldAreRefused() throws Exception {
        mvc.perform(post("/try/products/1001/quantity").param("state", "p=1001:1;b=").param("quantity", "0"))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(post("/try/products/1001/quantity").param("state", "p=1001:1;b=").param("quantity", "1000001"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void anItemWithNoBlueprintIsRefused() throws Exception {
        add("", TRITANIUM.value()).andExpect(status().isUnprocessableContent());
    }

    @Test
    void removingAProductOffersUndoAndKeepsItsUnits() throws Exception {
        add("", HAULER.value());

        mvc.perform(post("/try/products/1001/remove").param("state", "p=1001:3;b="))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"undo-remove\"")))
                .andExpect(content().string(containsString("name=\"quantity\" value=\"3\"")))
                .andExpect(content().string(containsString("value=\"p=;b=\"")));
        mvc.perform(post("/try/products/1001/restore").param("state", "p=;b=").param("quantity", "3"))
                .andExpect(content().string(containsString("value=\"p=1001:3;b=\"")));
    }

    @Test
    void buyingAPartIsKeptInTheState() throws Exception {
        esi.sell(COMPONENT.value(), "100.00");
        add("", HAULER.value());

        mvc.perform(post("/try/parts/1002/buy").param("state", "p=1001:1;b="))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("650 ISK")))
                .andExpect(content().string(containsString("value=\"p=1001:1;b=1002\"")));
    }

    @Test
    void aPlanHoldsAtMostTenProducts() throws Exception {
        var ten = "p=1001:1,1002:1,1003:1,1004:1,1005:1,1006:1,1007:1,1011:1,1012:1,1013:1;b=";

        add(ten, 1014)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"try-notice\"")))
                .andExpect(content().string(containsString("at most 10 products")))
                .andExpect(content().string(containsString("value=\"" + ten + "\"")));
        // another unit of a product it already holds is still fine
        add(ten, 1001).andExpect(content().string(containsString("p=1001:2,")));
    }

    @Test
    void aDamagedOrOversizedSavedPlanIsDrawnAsEmpty() throws Exception {
        mvc.perform(post("/try/render").param("state", "not a plan"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"p=;b=\"")));
        mvc.perform(post("/try/render").param("state", "p=1001:1,1001:5,9999:1,1002:2000000;b=77"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"p=1001:1;b=\"")));
    }

    @Test
    void searchNeedsNoSignInAndAddsThroughThePageNotARedirect() throws Exception {
        mvc.perform(get("/try/product-search").param("q", "hauler"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("hx-post=\"/try/products\"")))
                .andExpect(content().string(containsString("Test Hauler")));
    }
}
