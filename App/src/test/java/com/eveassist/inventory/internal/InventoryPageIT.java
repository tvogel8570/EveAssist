package com.eveassist.inventory.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eveassist.IntegrationTest;
import com.eveassist.esi.CharacterAsset;
import com.eveassist.identity.Viewer;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** The inventory page for each state a character can be in, and the polling that follows a running fetch. */
@AutoConfigureMockMvc
class InventoryPageIT extends IntegrationTest {

    private static final String ASSETS = "esi-assets.read_assets.v1";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    AssetFetcher fetcher;

    private RequestPostProcessor signedInAs(String sub) {
        return oidcLogin().idToken(token -> token.subject(sub)).authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private long link(Viewer viewer, String name, String state, String... scopes) {
        var eveId = 2_000_000_000L + ThreadLocalRandom.current().nextInt(1_000_000_000);
        jdbc.sql("""
                INSERT INTO character (public_id, owner_account_id, eve_character_id, name, owner_hash, scopes, state)
                VALUES (:id, :owner, :eve, :name, 'hash', :scopes, :state)
                """)
                .param("id", UUID.randomUUID()).param("owner", viewer.accountId().value()).param("eve", eveId)
                .param("name", name).param("scopes", scopes).param("state", state)
                .update();
        return eveId;
    }

    private void awaitIdle(Viewer viewer) {
        await().atMost(10, TimeUnit.SECONDS).until(() -> !fetcher.anyRunning(viewer));
    }

    @Test
    void showsAnEmptyStateWithNoCharacters() throws Exception {
        var sub = newSub();
        viewerFor(sub);

        mvc.perform(get("/inventory").with(signedInAs(sub)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"no-characters\"")))
                .andExpect(content().string(containsString("id=\"nav-inventory\"")))
                .andExpect(content().string(not(containsString("??"))));
    }

    @Test
    void openingThePageFetchesAndALaterOpeningShowsTheResult() throws Exception {
        var sub = newSub();
        var viewer = viewerFor(sub);
        var eveId = link(viewer, "Alt One", "ACTIVE", ASSETS);
        esi.assetsOf(eveId, List.of(new CharacterAsset(1, 34, 1500, 60003760L, "station", "Hangar", false, null),
                new CharacterAsset(2, 35, 20, 60003760L, "station", "Hangar", false, null)));

        mvc.perform(get("/inventory").with(signedInAs(sub))).andExpect(status().isOk());
        awaitIdle(viewer);

        // the status block does not start a fetch, unlike opening the page, so it shows the settled result
        var page = mvc.perform(get("/inventory/status").with(signedInAs(sub)))
                .andExpect(content().string(not(containsString("??"))))
                .andReturn().getResponse().getContentAsString();
        assertThat(page).contains("Alt One", "Up to date").containsPattern("<td class=\"num\">\\s*2\\s*</td>");
        assertThat(page).contains(" UTC");
    }

    @Test
    void showsEachStateWithItsOwnWordsAndAPathToFixIt() throws Exception {
        var sub = newSub();
        var viewer = viewerFor(sub);
        link(viewer, "Refused One", "NEEDS_REAUTHORIZATION", ASSETS);
        link(viewer, "No Scope One", "ACTIVE", "esi-universe.read_structures.v1");
        var broken = link(viewer, "Broken One", "ACTIVE", ASSETS);
        esi.failAssets(broken, "ESI is throttling char-asset for another 9 s");
        mvc.perform(get("/inventory").with(signedInAs(sub)));
        awaitIdle(viewer);

        var page = mvc.perform(get("/inventory/status").with(signedInAs(sub)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("??"))))
                .andReturn().getResponse().getContentAsString();

        assertThat(page).contains("Needs authorization", "Needs permission for assets", "Could not update",
                "ESI is throttling char-asset for another 9 s");
        assertThat(page).contains("href=\"/characters\"");
    }

    @Test
    void keepsPollingOnlyWhileAFetchRuns() throws Exception {
        var sub = newSub();
        var viewer = viewerFor(sub);
        var eveId = link(viewer, "Alt One", "ACTIVE", ASSETS);
        var gate = new CountDownLatch(1);
        esi.holdAssetCalls(gate);
        mvc.perform(get("/inventory").with(signedInAs(sub)));
        await().atMost(5, TimeUnit.SECONDS).until(() -> fetcher.anyRunning(viewer));

        mvc.perform(get("/inventory/status").with(signedInAs(sub)))
                .andExpect(content().string(containsString("hx-get=\"/inventory/status\"")))
                .andExpect(content().string(containsString("Updating")));

        gate.countDown();
        awaitIdle(viewer);
        assertThat(esi.assetsAsked()).contains(eveId);

        mvc.perform(get("/inventory/status").with(signedInAs(sub)))
                .andExpect(content().string(not(containsString("hx-get"))))
                .andExpect(content().string(containsString("Up to date")));
    }

    @Test
    void theRefreshButtonQueuesAFetchAndComesBack() throws Exception {
        var sub = newSub();
        var viewer = viewerFor(sub);
        var eveId = link(viewer, "Alt One", "ACTIVE", ASSETS);

        mvc.perform(post("/inventory/refresh").with(signedInAs(sub)).with(csrf()))
                .andExpect(redirectedUrl("/inventory"));
        awaitIdle(viewer);

        assertThat(esi.assetsAsked()).contains(eveId);
    }

    @Test
    void showsNothingOfAnotherAccountsCharacters() throws Exception {
        var other = viewerFor(newSub());
        link(other, "Someone Else", "ACTIVE", ASSETS);
        var sub = newSub();
        viewerFor(sub);

        mvc.perform(get("/inventory").with(signedInAs(sub)))
                .andExpect(content().string(not(containsString("Someone Else"))));
    }

    @Test
    void escapesACharacterNameThatHoldsMarkup() throws Exception {
        var sub = newSub();
        link(viewerFor(sub), "<script>alert(1)</script>", "ACTIVE", ASSETS);

        mvc.perform(get("/inventory/status").with(signedInAs(sub)))
                .andExpect(content().string(not(containsString("<script>alert(1)"))))
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)")));
    }

    @Test
    void theAddressesNeedASignedInUserAndARefreshNeedsACsrfToken() throws Exception {
        mvc.perform(get("/inventory")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/inventory/status")).andExpect(status().is3xxRedirection());
        var sub = newSub();
        viewerFor(sub);
        mvc.perform(post("/inventory/refresh").with(signedInAs(sub))).andExpect(status().isForbidden());
    }
}
