package com.eveassist.market.internal.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eveassist.IntegrationTest;
import com.eveassist.esi.PublicContract;
import com.eveassist.esi.PublicContractItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Renders the blueprint copy page with each case it has to handle. */
@AutoConfigureMockMvc
class BlueprintCopyPagesIT extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    private RequestPostProcessor signedIn() {
        accounts.signedIn("bpc-sub", "Test Pilot");
        return oidcLogin().idToken(token -> token.subject("bpc-sub")).authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Test
    void requiresSignIn() throws Exception {
        mvc.perform(get("/market/blueprint-copies")).andExpect(status().is3xxRedirection());
    }

    @Test
    void showsTheStartPageWithoutAPrice() throws Exception {
        mvc.perform(get("/market/blueprint-copies").with(signedIn()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Refresh contracts")))
                .andExpect(content().string(not(containsString("bpc-price"))));
    }

    @Test
    void findsABlueprintByName() throws Exception {
        mvc.perform(get("/market/blueprint-copies").param("q", "hauler").with(signedIn()))
                .andExpect(content().string(containsString("Test Hauler Blueprint")));
        mvc.perform(get("/market/blueprint-copies").param("q", "nothing like it").with(signedIn()))
                .andExpect(content().string(containsString("No blueprint found")));
    }

    @Test
    void saysSoWhenNoContractOffersTheCopy() throws Exception {
        mvc.perform(get("/market/blueprint-copies").param("type", "2001").with(signedIn()))
                .andExpect(content().string(containsString("bpc-none")));
    }

    @Test
    void ignoresATypeThatIsNotABlueprintAndKeepsMeAndTeInRange() throws Exception {
        mvc.perform(get("/market/blueprint-copies").param("type", "34").with(signedIn()))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("bpc-price"))));
        mvc.perform(get("/market/blueprint-copies").param("type", "2001").param("me", "99").param("te", "-4")
                        .with(signedIn()))
                .andExpect(content().string(containsString("value=\"10\"")))
                .andExpect(content().string(containsString("value=\"0\"")));
    }

    @Test
    void refreshesAndThenShowsThePrice() throws Exception {
        esi.contract(new PublicContract(9_500_001, "item_exchange", new BigDecimal("500000"), 5,
                        Instant.now().minusSeconds(60), Instant.now().plusSeconds(86400), 60003760L, 0.01),
                List.of(new PublicContractItem(1, 2001, 1, true, true, 10, 20, 5)));

        mvc.perform(post("/market/blueprint-copies/refresh").param("type", "2001").param("me", "10").param("te", "20")
                        .with(signedIn()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/market/blueprint-copies?*"));
        mvc.perform(get("/market/blueprint-copies").param("type", "2001").param("me", "10").param("te", "20")
                        .with(signedIn()))
                .andExpect(content().string(containsString("100,000.00")))
                .andExpect(content().string(containsString("Contract list read")));
    }
}
