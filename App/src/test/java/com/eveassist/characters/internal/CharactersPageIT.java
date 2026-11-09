package com.eveassist.characters.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eveassist.IntegrationTest;
import com.eveassist.characters.Feature;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** The Characters page, rendered for each case it has to show (empty, each status, each message). */
@AutoConfigureMockMvc
@Import(TestCharactersConfig.class)
class CharactersPageIT extends IntegrationTest {

    private static final Set<String> BOTH = Feature.INVENTORY.scopes();

    @Autowired
    MockMvc mvc;

    @Autowired
    CharacterRepository characters;

    @Autowired
    FeatureRepository features;

    @Autowired
    TokenCipher cipher;

    @Autowired
    JdbcClient jdbc;

    private static long newEveId() {
        return 2_000_000_000L + ThreadLocalRandom.current().nextInt(1_000_000_000);
    }

    private RequestPostProcessor signedInAs(String sub) {
        return oidcLogin().idToken(token -> token.subject(sub))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    /** The visible text of a page: tags removed and white space collapsed. */
    private static String text(String html) {
        return html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").replace(" ,", ",");
    }

    private long link(String sub, String name, Set<String> scopes) {
        var eveId = newEveId();
        characters.link(viewerFor(sub).accountId(), eveId, name, "hash", scopes, cipher.encrypt(eveId, "refresh"));
        return eveId;
    }

    @Test
    void showsTheFeaturesAndAnEmptyStateWithNoFeatureOnAndNoCharacters() throws Exception {
        var sub = newSub();
        viewerFor(sub);

        mvc.perform(get("/characters").with(signedInAs(sub)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"no-feature-hint\"")))
                .andExpect(content().string(containsString("id=\"no-characters\"")))
                .andExpect(content().string(containsString("esi-assets.read_assets.v1")))
                .andExpect(content().string(containsString("esi-universe.read_structures.v1")))
                .andExpect(content().string(not(containsString("href=\"/characters/link\""))))
                .andExpect(content().string(not(containsString("??"))));
    }

    @Test
    void offersLinkingOnceAFeatureIsOn() throws Exception {
        var sub = newSub();
        features.turnOn(viewerFor(sub).accountId(), Feature.INVENTORY);

        mvc.perform(get("/characters").with(signedInAs(sub)))
                .andExpect(content().string(containsString("href=\"/characters/link\"")))
                .andExpect(content().string(not(containsString("id=\"no-feature-hint\""))))
                .andExpect(content().string(not(containsString("??"))));
    }

    @Test
    void showsEachStatusWithItsOwnWordsAndActions() throws Exception {
        var sub = newSub();
        features.turnOn(viewerFor(sub).accountId(), Feature.INVENTORY);
        link(sub, "Alt One", BOTH);
        link(sub, "Alt Two", Set.of("esi-universe.read_structures.v1"));
        var refusedEveId = link(sub, "Alt Three", BOTH);
        jdbc.sql("UPDATE character SET state = 'NEEDS_REAUTHORIZATION' WHERE eve_character_id = :eve")
                .param("eve", refusedEveId).update();

        var page = mvc.perform(get("/characters").with(signedInAs(sub)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("??"))))
                .andReturn().getResponse().getContentAsString();

        var visible = text(page);
        assertThat(visible).contains("Alt One", "Alt Two", "Alt Three");
        assertThat(visible).contains("Active", "Needs permissions", "EVE refused this character");
        assertThat(visible).contains("Permissions: assets, structure names");
        // Alt Two lacks assets
        assertThat(visible).contains("Missing: assets");
    }

    @Test
    void showsAnotherAccountsCharactersToNoOne() throws Exception {
        var other = newSub();
        link(other, "Someone Else", BOTH);
        var sub = newSub();
        viewerFor(sub);

        mvc.perform(get("/characters").with(signedInAs(sub)))
                .andExpect(content().string(not(containsString("Someone Else"))));
    }

    @Test
    void escapesACharacterNameThatHoldsMarkup() throws Exception {
        var sub = newSub();
        link(sub, "<script>alert(1)</script>", BOTH);

        mvc.perform(get("/characters").with(signedInAs(sub)))
                .andExpect(content().string(not(containsString("<script>alert(1)"))))
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)")));
    }

    @Test
    void showsAMessageForEachLinkingResultAndIgnoresAnythingElse() throws Exception {
        var sub = newSub();
        viewerFor(sub);
        for (var result : new String[] {"LINKED", "RENEWED", "ON_ANOTHER_ACCOUNT", "STATE_MISMATCH", "REFUSED",
                "declined", "no-feature"}) {
            mvc.perform(get("/characters").param("linking", result).with(signedInAs(sub)))
                    .andExpect(content().string(containsString("id=\"linking-message\"")))
                    .andExpect(content().string(not(containsString("??"))));
        }
        mvc.perform(get("/characters").param("linking", "<b>hi</b>").with(signedInAs(sub)))
                .andExpect(content().string(not(containsString("id=\"linking-message\""))))
                .andExpect(content().string(not(containsString("<b>hi</b>"))));
    }

    @Test
    void turnsAFeatureOnAndOffThroughHtmx() throws Exception {
        var sub = newSub();
        var viewer = viewerFor(sub);

        mvc.perform(post("/characters/features/INVENTORY").param("on", "true").header("HX-Request", "true")
                        .with(signedInAs(sub)).with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(header().string("HX-Refresh", "true"));
        assertThat(features.turnedOn(viewer.accountId())).containsExactly(Feature.INVENTORY);

        // an unchecked toggle sends no value
        mvc.perform(post("/characters/features/INVENTORY").header("HX-Request", "true")
                        .with(signedInAs(sub)).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(features.turnedOn(viewer.accountId())).isEmpty();
    }

    @Test
    void turningAFeatureOnWithoutHtmxRedirectsBackToThePage() throws Exception {
        var sub = newSub();
        viewerFor(sub);

        mvc.perform(post("/characters/features/INVENTORY").param("on", "true").with(signedInAs(sub)).with(csrf()))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/characters"));
    }

    @Test
    void refusesAnUnknownFeatureAndAMissingCsrfToken() throws Exception {
        var sub = newSub();
        viewerFor(sub);

        mvc.perform(post("/characters/features/NOPE").param("on", "true").with(signedInAs(sub)).with(csrf()))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/characters/features/INVENTORY").param("on", "true").with(signedInAs(sub)))
                .andExpect(status().isForbidden());
    }

    @Test
    void removesACharacterWithItsTokenButNotAnotherAccountsCharacter() throws Exception {
        var sub = newSub();
        var eveId = link(sub, "Alt One", BOTH);
        var viewer = viewerFor(sub);
        var id = characters.listFor(viewer.accountId()).getFirst().id();
        var otherSub = newSub();
        var otherEveId = link(otherSub, "Theirs", BOTH);
        var otherId = characters.listFor(viewerFor(otherSub).accountId()).getFirst().id();

        mvc.perform(post("/characters/{id}/remove", otherId).with(signedInAs(sub)).with(csrf()))
                .andExpect(redirectedUrl("/characters"));
        assertThat(characters.findToken(otherEveId)).isPresent();

        mvc.perform(post("/characters/{id}/remove", id).with(signedInAs(sub)).with(csrf()))
                .andExpect(redirectedUrl("/characters?removed"));
        assertThat(characters.findToken(eveId)).isEmpty();
        assertThat(characters.listFor(viewer.accountId())).isEmpty();
    }

    @Test
    void servesAPortraitOnlyToItsOwner() throws Exception {
        var sub = newSub();
        var eveId = link(sub, "Alt One", BOTH);
        characters.storePortrait(eveId, FakePortraitSource.IMAGE);
        var id = characters.listFor(viewerFor(sub).accountId()).getFirst().id();

        mvc.perform(get("/characters/{id}/portrait", id).with(signedInAs(sub)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("Cache-Control", containsString("private")))
                .andExpect(content().bytes(FakePortraitSource.IMAGE));
        mvc.perform(get("/characters/{id}/portrait", id).with(signedInAs(newSubWithAccount())))
                .andExpect(status().isNotFound());
        mvc.perform(get("/characters").with(signedInAs(sub)))
                .andExpect(content().string(containsString("/characters/" + id + "/portrait")));
    }

    @Test
    void showsInitialsWhenThereIsNoPortrait() throws Exception {
        var sub = newSub();
        link(sub, "Zed Alt", BOTH);

        mvc.perform(get("/characters").with(signedInAs(sub)))
                .andExpect(content().string(not(containsString("/portrait"))))
                .andExpect(content().string(containsString(">Z<")));
    }

    @Test
    void theNavigationLinksToThePage() throws Exception {
        var sub = newSub();
        viewerFor(sub);

        mvc.perform(get("/characters").with(signedInAs(sub)))
                .andExpect(content().string(containsString("id=\"nav-characters\"")));
    }

    @Test
    void thePageNeedsASignedInUser() throws Exception {
        mvc.perform(get("/characters")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/characters/{id}/portrait", java.util.UUID.randomUUID()))
                .andExpect(status().is3xxRedirection());
    }

    private String newSubWithAccount() {
        var sub = newSub();
        viewerFor(sub);
        return sub;
    }
}
