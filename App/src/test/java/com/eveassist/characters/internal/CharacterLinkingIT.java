package com.eveassist.characters.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eveassist.IntegrationTest;
import com.eveassist.characters.Feature;
import jakarta.servlet.http.Cookie;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The linking flow through the real filters. The state lives in a Spring Session, which replaces MockMvc's own, so each
 * test carries the session cookie from the first request to the callback, as a browser does.
 */
@AutoConfigureMockMvc
@Import(TestCharactersConfig.class)
class CharacterLinkingIT extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    FakeEveSso sso;

    @Autowired
    FakePortraitSource portraits;

    @Autowired
    FeatureRepository features;

    @Autowired
    CharacterRepository characters;

    @Autowired
    TokenCipher cipher;

    @BeforeEach
    void resetSso() {
        sso.reset();
        portraits.reset();
    }

    private static long newEveId() {
        return 2_000_000_000L + ThreadLocalRandom.current().nextInt(1_000_000_000);
    }

    private RequestPostProcessor signedInAs(String sub) {
        return oidcLogin().idToken(token -> token.subject(sub));
    }

    /** Starts linking and returns the browser's session cookie. */
    private Cookie startLinking(String sub) throws Exception {
        var result = mvc.perform(get("/characters/link").with(signedInAs(sub))).andReturn();
        return result.getResponse().getCookie("SESSION");
    }

    @Test
    void sendsTheBrowserToSsoAskingOnlyForTheScopesOfTurnedOnFeatures() throws Exception {
        var sub = newSub();
        features.turnOn(viewerFor(sub).accountId(), Feature.INVENTORY);

        var response = mvc.perform(get("/characters/link").with(signedInAs(sub)))
                .andExpect(status().isFound())
                .andReturn().getResponse();

        assertThat(response.getRedirectedUrl()).isEqualTo("https://sso.invalid/authorize?state=" + sso.askedState);
        assertThat(sso.askedScopes).isEqualTo(Feature.INVENTORY.scopes());
        assertThat(sso.askedState).hasSizeGreaterThanOrEqualTo(40);
    }

    @Test
    void asksForNothingWhenNoFeatureIsOn() throws Exception {
        var sub = newSub();
        viewerFor(sub);

        mvc.perform(get("/characters/link").with(signedInAs(sub)))
                .andExpect(redirectedUrl("/characters?linking=no-feature"));

        assertThat(sso.askedScopes).isNull();
    }

    @Test
    void linksTheCharacterWhenTheStateComesBack() throws Exception {
        var sub = newSub();
        var viewer = viewerFor(sub);
        features.turnOn(viewer.accountId(), Feature.INVENTORY);
        var eveId = newEveId();
        sso.nextLink = sso.link(eveId, "Alt One", "refresh-1");
        var session = startLinking(sub);

        mvc.perform(get("/characters/link/callback").param("code", "the-code").param("state", sso.askedState)
                        .with(signedInAs(sub)).cookie(session))
                .andExpect(redirectedUrl("/characters?linking=LINKED"));

        assertThat(characters.listFor(viewer.accountId())).singleElement()
                .satisfies(c -> assertThat(c.name()).isEqualTo("Alt One"));
        assertThat(cipher.decrypt(eveId, characters.findToken(eveId).orElseThrow())).isEqualTo("refresh-1");
    }

    @Test
    void storesThePortraitWhenTheCharacterIsLinked() throws Exception {
        var sub = newSub();
        var viewer = viewerFor(sub);
        features.turnOn(viewer.accountId(), Feature.INVENTORY);
        sso.nextLink = sso.link(newEveId(), "Alt One", "refresh-1");
        var session = startLinking(sub);

        mvc.perform(get("/characters/link/callback").param("code", "c").param("state", sso.askedState)
                .with(signedInAs(sub)).cookie(session));

        var id = characters.listFor(viewer.accountId()).getFirst();
        assertThat(id.hasPortrait()).isTrue();
        assertThat(characters.findPortrait(viewer.accountId(), id.id())).contains(FakePortraitSource.IMAGE);
    }

    @Test
    void linksWithoutAPortraitWhenTheImageServerDoesNotAnswer() throws Exception {
        var sub = newSub();
        var viewer = viewerFor(sub);
        features.turnOn(viewer.accountId(), Feature.INVENTORY);
        sso.nextLink = sso.link(newEveId(), "Alt One", "refresh-1");
        portraits.available = false;
        var session = startLinking(sub);

        mvc.perform(get("/characters/link/callback").param("code", "c").param("state", sso.askedState)
                        .with(signedInAs(sub)).cookie(session))
                .andExpect(redirectedUrl("/characters?linking=LINKED"));

        assertThat(characters.listFor(viewer.accountId())).singleElement()
                .satisfies(c -> assertThat(c.hasPortrait()).isFalse());
    }

    @Test
    void doesNotFetchAPortraitForACharacterOnAnotherAccount() throws Exception {
        var eveId = newEveId();
        characters.link(viewerFor(newSub()).accountId(), eveId, "Alt One", "hash", Set.of("a"),
                cipher.encrypt(eveId, "mine"));
        var sub = newSub();
        features.turnOn(viewerFor(sub).accountId(), Feature.INVENTORY);
        sso.nextLink = sso.link(eveId, "Alt One", "theirs");
        var session = startLinking(sub);

        mvc.perform(get("/characters/link/callback").param("code", "c").param("state", sso.askedState)
                .with(signedInAs(sub)).cookie(session));

        assertThat(portraits.fetches).isZero();
    }

    @Test
    void refusesAWrongStateAndLinksNothing() throws Exception {
        var sub = newSub();
        var viewer = viewerFor(sub);
        features.turnOn(viewer.accountId(), Feature.INVENTORY);
        sso.nextLink = sso.link(newEveId(), "Alt One", "refresh-1");
        var session = startLinking(sub);

        mvc.perform(get("/characters/link/callback").param("code", "the-code").param("state", "forged")
                        .with(signedInAs(sub)).cookie(session))
                .andExpect(redirectedUrl("/characters?linking=STATE_MISMATCH"));

        assertThat(characters.listFor(viewer.accountId())).isEmpty();
        assertThat(sso.exchanges).isZero();
    }

    @Test
    void refusesACallbackWithNoLinkingInProgress() throws Exception {
        var sub = newSub();
        viewerFor(sub);

        mvc.perform(get("/characters/link/callback").param("code", "the-code").param("state", "anything")
                        .with(signedInAs(sub)))
                .andExpect(redirectedUrl("/characters?linking=STATE_MISMATCH"));

        assertThat(sso.exchanges).isZero();
    }

    @Test
    void aStateWorksOnlyOnce() throws Exception {
        var sub = newSub();
        features.turnOn(viewerFor(sub).accountId(), Feature.INVENTORY);
        sso.nextLink = sso.link(newEveId(), "Alt One", "refresh-1");
        var session = startLinking(sub);
        var state = sso.askedState;
        mvc.perform(get("/characters/link/callback").param("code", "c").param("state", state)
                        .with(signedInAs(sub)).cookie(session))
                .andExpect(redirectedUrl("/characters?linking=LINKED"));

        mvc.perform(get("/characters/link/callback").param("code", "c").param("state", state)
                        .with(signedInAs(sub)).cookie(session))
                .andExpect(redirectedUrl("/characters?linking=STATE_MISMATCH"));

        assertThat(sso.exchanges).isEqualTo(1);
    }

    @Test
    void reportsACharacterThatIsOnAnotherAccount() throws Exception {
        var eveId = newEveId();
        characters.link(viewerFor(newSub()).accountId(), eveId, "Alt One", "hash", Set.of("a"),
                cipher.encrypt(eveId, "mine"));
        var sub = newSub();
        features.turnOn(viewerFor(sub).accountId(), Feature.INVENTORY);
        sso.nextLink = sso.link(eveId, "Alt One", "theirs");
        var session = startLinking(sub);

        mvc.perform(get("/characters/link/callback").param("code", "c").param("state", sso.askedState)
                        .with(signedInAs(sub)).cookie(session))
                .andExpect(redirectedUrl("/characters?linking=ON_ANOTHER_ACCOUNT"));

        assertThat(cipher.decrypt(eveId, characters.findToken(eveId).orElseThrow())).isEqualTo("mine");
    }

    @Test
    void reportsRefusalFromSsoAndADeclinedConsent() throws Exception {
        var sub = newSub();
        features.turnOn(viewerFor(sub).accountId(), Feature.INVENTORY);
        sso.failWith = new EveSso.SsoException("refused");
        var session = startLinking(sub);

        mvc.perform(get("/characters/link/callback").param("code", "c").param("state", sso.askedState)
                        .with(signedInAs(sub)).cookie(session))
                .andExpect(redirectedUrl("/characters?linking=REFUSED"));
        mvc.perform(get("/characters/link/callback").param("error", "access_denied").with(signedInAs(sub)))
                .andExpect(redirectedUrl("/characters?linking=declined"));
    }

    @Test
    void theLinkingAddressesNeedASignedInUser() throws Exception {
        mvc.perform(get("/characters/link")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/characters/link/callback").param("code", "c").param("state", "s"))
                .andExpect(status().is3xxRedirection());
        assertThat(sso.exchanges).isZero();
    }
}
