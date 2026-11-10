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
import org.junit.jupiter.api.BeforeEach;
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

    @Autowired
    FakeEveSso sso;

    @BeforeEach
    void resetSso() {
        sso.reset();
    }

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
        assertThat(visible).contains("Active", "Needs permissions", "no working saved access");
        assertThat(visible).contains("Permissions: assets, structure names");
        // Alt Two lacks assets
        assertThat(visible).contains("Missing: assets");
    }

    @Test
    void showsGrantedScopesThatNoTurnedOnFeatureUsesAndOffersToDropThem() throws Exception {
        var sub = newSub();
        viewerFor(sub);
        link(sub, "Alt One", BOTH);

        var withFeatureOff = text(mvc.perform(get("/characters").with(signedInAs(sub)))
                .andExpect(content().string(not(containsString("??"))))
                .andReturn().getResponse().getContentAsString());

        assertThat(withFeatureOff).contains("Not in use: assets, structure names");
        assertThat(withFeatureOff).contains("Drop unused permissions");
        assertThat(withFeatureOff).doesNotContain("Permissions: assets");

        features.turnOn(viewerFor(sub).accountId(), Feature.INVENTORY);

        var withFeatureOn = text(mvc.perform(get("/characters").with(signedInAs(sub)))
                .andReturn().getResponse().getContentAsString());

        assertThat(withFeatureOn).contains("Permissions: assets, structure names");
        assertThat(withFeatureOn).doesNotContain("Not in use").doesNotContain("Drop unused permissions");
    }

    @Test
    void dropsAccessByRevokingTheTokenAtEveAndLinkingAgain() throws Exception {
        var sub = newSub();
        var eveId = link(sub, "Alt One", BOTH);
        var id = characters.listFor(viewerFor(sub).accountId()).getFirst().id();

        mvc.perform(post("/characters/{id}/drop-access", id).with(signedInAs(sub)).with(csrf()))
                .andExpect(redirectedUrl("/characters/link"));

        assertThat(sso.revoked).containsExactly("refresh");
        assertThat(characters.findToken(eveId)).isEmpty();
        assertThat(characters.listFor(viewerFor(sub).accountId())).singleElement().satisfies(c -> {
            assertThat(c.scopes()).isEmpty();
            assertThat(c.state()).isEqualTo("NEEDS_REAUTHORIZATION");
        });
    }

    @Test
    void doesNotLinkAgainWhenEveDidNotConfirmTheRevocation() throws Exception {
        var sub = newSub();
        link(sub, "Alt One", BOTH);
        var id = characters.listFor(viewerFor(sub).accountId()).getFirst().id();
        sso.revokeConfirmed = false;

        mvc.perform(post("/characters/{id}/drop-access", id).with(signedInAs(sub)).with(csrf()))
                .andExpect(redirectedUrl("/characters?dropped=unrevoked"));
        mvc.perform(get("/characters").param("dropped", "unrevoked").with(signedInAs(sub)))
                .andExpect(content().string(containsString("id=\"revoke-failed-message\"")))
                .andExpect(content().string(not(containsString("??"))));
    }

    @Test
    void neverDropsOrRevokesAnotherAccountsCharacter() throws Exception {
        var otherSub = newSub();
        var otherEveId = link(otherSub, "Theirs", BOTH);
        var otherId = characters.listFor(viewerFor(otherSub).accountId()).getFirst().id();
        var sub = newSub();
        viewerFor(sub);

        mvc.perform(post("/characters/{id}/drop-access", otherId).with(signedInAs(sub)).with(csrf()))
                .andExpect(redirectedUrl("/characters"));

        assertThat(sso.revoked).isEmpty();
        assertThat(characters.findToken(otherEveId)).isPresent();
    }

    @Test
    void revokesTheTokenAtEveWhenACharacterIsRemoved() throws Exception {
        var sub = newSub();
        link(sub, "Alt One", BOTH);
        var id = characters.listFor(viewerFor(sub).accountId()).getFirst().id();

        mvc.perform(post("/characters/{id}/remove", id).with(signedInAs(sub)).with(csrf()))
                .andExpect(redirectedUrl("/characters?removed"));

        assertThat(sso.revoked).containsExactly("refresh");
    }

    @Test
    void removesTheCharacterEvenWhenEveDoesNotConfirmAndSaysSo() throws Exception {
        var sub = newSub();
        var eveId = link(sub, "Alt One", BOTH);
        var id = characters.listFor(viewerFor(sub).accountId()).getFirst().id();
        sso.revokeConfirmed = false;

        mvc.perform(post("/characters/{id}/remove", id).with(signedInAs(sub)).with(csrf()))
                .andExpect(redirectedUrl("/characters?removed=unrevoked"));

        assertThat(characters.findToken(eveId)).isEmpty();
        mvc.perform(get("/characters").param("removed", "unrevoked").with(signedInAs(sub)))
                .andExpect(content().string(containsString("id=\"revoke-failed-message\"")))
                .andExpect(content().string(containsString("id=\"removed-message\"")));
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
    void corporationAssetsTurnsInventoryOnWithItAndInventoryOffTurnsItOff() throws Exception {
        var sub = newSub();
        var viewer = viewerFor(sub);

        mvc.perform(post("/characters/features/CORPORATION_ASSETS").param("on", "true").header("HX-Request", "true")
                        .with(signedInAs(sub)).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(features.turnedOn(viewer.accountId()))
                .containsExactlyInAnyOrder(Feature.INVENTORY, Feature.CORPORATION_ASSETS);

        // the corporation permissions are asked for with the inventory ones, and nothing else is
        assertThat(Feature.CORPORATION_ASSETS.scopes()).containsExactlyInAnyOrder(
                "esi-assets.read_corporation_assets.v1", "esi-corporations.read_divisions.v1",
                "esi-characters.read_corporation_roles.v1");

        // turning the corporation feature off leaves inventory on
        mvc.perform(post("/characters/features/CORPORATION_ASSETS").header("HX-Request", "true")
                        .with(signedInAs(sub)).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(features.turnedOn(viewer.accountId())).containsExactly(Feature.INVENTORY);

        // and turning inventory off takes the corporation feature with it
        mvc.perform(post("/characters/features/CORPORATION_ASSETS").param("on", "true").header("HX-Request", "true")
                        .with(signedInAs(sub)).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(post("/characters/features/INVENTORY").header("HX-Request", "true")
                        .with(signedInAs(sub)).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(features.turnedOn(viewer.accountId())).isEmpty();
    }

    @Test
    void thePageOffersTheCorporationFeatureAndListsItsPermissions() throws Exception {
        var sub = newSub();
        viewerFor(sub);

        var page = text(mvc.perform(get("/characters").with(signedInAs(sub))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(page).contains("Corporation assets").contains("esi-assets.read_corporation_assets.v1")
                .contains("esi-corporations.read_divisions.v1").contains("esi-characters.read_corporation_roles.v1");
    }

    @Test
    void aCharacterThatGrantedOnlyInventoryIsToldToAddTheCorporationPermissions() throws Exception {
        var sub = newSub();
        var viewer = viewerFor(sub);
        features.turnOn(viewer.accountId(), Feature.INVENTORY);
        features.turnOn(viewer.accountId(), Feature.CORPORATION_ASSETS);
        var eveId = newEveId();
        characters.link(viewer.accountId(), eveId, "Old Token", "hash", BOTH, cipher.encrypt(eveId, "refresh"));

        var page = text(mvc.perform(get("/characters").with(signedInAs(sub))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(page).contains("Missing: corporation assets, corporation roles, corporation hangar names");
    }

    @Test
    void strikesThroughTheDirectorOnlyPermissionsOfACharacterKnownNotToBeADirector() throws Exception {
        var sub = newSub();
        var viewer = viewerFor(sub);
        features.turnOn(viewer.accountId(), Feature.INVENTORY);
        features.turnOn(viewer.accountId(), Feature.CORPORATION_ASSETS);
        var all = new java.util.HashSet<>(BOTH);
        all.addAll(Feature.CORPORATION_ASSETS.scopes());
        var boss = link(sub, "Cass Director", all);
        var member = link(sub, "Henre Member", all);
        var unknown = link(sub, "Not Yet Read", all);
        var bossId = publicId(boss);
        var memberId = publicId(member);
        for (var entry : java.util.Map.of(bossId, true, memberId, false).entrySet()) {
            jdbc.sql("""
                    INSERT INTO character_corporation (character_id, eve_corporation_id, director, checked_at,
                        corporation_checked_at) VALUES (:id, 98000001, :director, now(), now())
                    """).param("id", entry.getKey()).param("director", entry.getValue()).update();
        }

        var html = mvc.perform(get("/characters").with(signedInAs(sub))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var memberCard = card(html, memberId);
        assertThat(memberCard).contains("line-through");
        assertThat(memberCard).contains("title=\"This character is not a Director");
        // both Director-only permissions are struck, and the others are not
        assertThat(memberCard.split("line-through", -1)).hasSize(3);
        assertThat(card(html, bossId)).doesNotContain("line-through");
        assertThat(card(html, publicId(unknown))).doesNotContain("line-through");
    }

    private java.util.UUID publicId(long eveId) {
        return jdbc.sql("SELECT public_id FROM character WHERE eve_character_id = :eve").param("eve", eveId)
                .query(java.util.UUID.class).single();
    }

    /** The part of the page for one character's card. */
    private static String card(String html, java.util.UUID id) {
        var start = html.indexOf("id=\"character-" + id + "\"");
        var end = html.indexOf("id=\"character-", start + 10);
        return html.substring(start, end < 0 ? html.length() : end);
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
