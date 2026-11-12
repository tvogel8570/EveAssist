package com.eveassist.characters.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eveassist.IntegrationTest;
import com.eveassist.identity.internal.KeycloakAdmin;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** The account page's two promises, through the real filters: take your data, and delete it. */
@AutoConfigureMockMvc
@Import(TestCharactersConfig.class)
class AccountDataPagesIT extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    CharacterRepository characters;

    @Autowired
    TokenCipher cipher;

    @Autowired
    FakeEveSso sso;

    @Autowired
    JdbcClient jdbc;

    @MockitoBean
    KeycloakAdmin keycloak;

    @BeforeEach
    void reset() {
        sso.reset();
        when(keycloak.deleteUser(anyString())).thenReturn(true);
    }

    private RequestPostProcessor signedInAs(String sub) {
        return oidcLogin().idToken(token -> token.subject(sub)).authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    /** An account with one linked character (token "secret-refresh-token") and one plan. */
    private String accountWithData() {
        var sub = newSub();
        var eveId = 2_000_000_000L + ThreadLocalRandom.current().nextInt(1_000_000_000);
        var account = viewerFor(sub).accountId();
        characters.link(account, eveId, "Export Pilot", "owner-hash-" + eveId, Set.of("esi-assets.read_assets.v1"),
                cipher.encrypt(eveId, "secret-refresh-token"));
        jdbc.sql("INSERT INTO plan (public_id, owner_account_id, name) VALUES (:id, :owner, 'Export Hulls')")
                .param("id", UUID.randomUUID()).param("owner", account.value()).update();
        return sub;
    }

    private int accounts(String sub) {
        return jdbc.sql("SELECT count(*) FROM account WHERE keycloak_sub = :sub").param("sub", sub)
                .query(Integer.class).single();
    }

    @Test
    void theExportIsAJsonDownloadOfTheAccountsCharactersAndPlans() throws Exception {
        var sub = accountWithData();

        mvc.perform(get("/account/export").with(signedInAs(sub)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("eveassist-export-")))
                .andExpect(jsonPath("$.application").value("EveAssist"))
                .andExpect(jsonPath("$.account.displayName").value("Test Pilot"))
                .andExpect(jsonPath("$.characters[0].name").value("Export Pilot"))
                .andExpect(jsonPath("$.characters[0].grantedScopes[0]").value("esi-assets.read_assets.v1"))
                .andExpect(jsonPath("$.plans[0].name").value("Export Hulls"))
                .andExpect(jsonPath("$.plans[0].products").isArray())
                .andExpect(jsonPath("$.features").isArray());
    }

    @Test
    void theExportNeverHoldsATokenOrAnotherAccountsData() throws Exception {
        var mine = accountWithData();
        accountWithData(); // another account, with its own character and plan

        var body = mvc.perform(get("/account/export").with(signedInAs(mine))).andReturn().getResponse()
                .getContentAsString();

        assertThat(body).doesNotContain("secret-refresh-token").doesNotContainIgnoringCase("ciphertext")
                .doesNotContainIgnoringCase("nonce").doesNotContain("keycloak_sub");
        assertThat(body.split("Export Pilot", -1)).hasSize(2); // exactly one character of that name
    }

    @Test
    void theExportNeedsASignIn() throws Exception {
        mvc.perform(get("/account/export")).andExpect(status().is3xxRedirection());
    }

    @Test
    void theConfirmationPageCountsWhatWillBeDeletedAndDeletesNothing() throws Exception {
        var sub = accountWithData();

        mvc.perform(get("/account/delete").with(signedInAs(sub)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("1 linked characters")))
                .andExpect(content().string(containsString("1 plans")))
                .andExpect(content().string(containsString("id=\"confirm\"")))
                .andExpect(content().string(containsString("id=\"confirm-delete\"")))
                .andExpect(content().string(containsString("up to 28 days")));

        assertThat(accounts(sub)).isEqualTo(1);
    }

    @Test
    void withoutTheCheckboxNothingIsDeleted() throws Exception {
        var sub = accountWithData();

        mvc.perform(post("/account/delete").with(signedInAs(sub)).with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().string(containsString("id=\"not-confirmed\"")));

        assertThat(accounts(sub)).isEqualTo(1);
        assertThat(sso.revoked).isEmpty();
    }

    @Test
    void withoutACsrfTokenNothingIsDeleted() throws Exception {
        var sub = accountWithData();

        mvc.perform(post("/account/delete").with(signedInAs(sub)).param("confirm", "true"))
                .andExpect(status().isForbidden());

        assertThat(accounts(sub)).isEqualTo(1);
    }

    @Test
    void confirmingDeletesTheAccountRevokesTheTokenAndLandsOnTheHomePage() throws Exception {
        var sub = accountWithData();

        mvc.perform(post("/account/delete").with(signedInAs(sub)).with(csrf()).param("confirm", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/?deleted=done"));

        assertThat(accounts(sub)).isZero();
        assertThat(sso.revoked).containsExactly("secret-refresh-token");
        verify(keycloak).deleteUser(sub);
    }

    @Test
    void whenKeycloakCannotBeReachedTheUserIsToldTheirDataIsGoneAnyway() throws Exception {
        var sub = accountWithData();
        when(keycloak.deleteUser(anyString())).thenReturn(false);

        mvc.perform(post("/account/delete").with(signedInAs(sub)).with(csrf()).param("confirm", "true"))
                .andExpect(redirectedUrl("/?deleted=partial"));

        assertThat(accounts(sub)).isZero();
    }

    @Test
    void theAccountPageOffersBothAndLinksThePolicy() throws Exception {
        var sub = accountWithData();

        mvc.perform(get("/account").with(signedInAs(sub)))
                .andExpect(content().string(containsString("id=\"export-data\"")))
                .andExpect(content().string(containsString("id=\"delete-account\"")))
                .andExpect(content().string(not(containsString("secret-refresh-token"))));
    }
}
