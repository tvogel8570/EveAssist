package com.eveassist.characters.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.eveassist.IntegrationTest;
import com.eveassist.characters.Feature;
import com.eveassist.esi.TokenGrant;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/** The access-token rules: refresh shortly before expiry, one refresh at a time, newest refresh token kept. */
@Import(TestCharactersConfig.class)
class CharacterAccessTokensIT extends IntegrationTest {

    private static final String ASSETS = "esi-assets.read_assets.v1";
    private static final Set<String> BOTH = Feature.INVENTORY.scopes();

    @Autowired
    CharacterAccessTokens tokens;

    @Autowired
    CharacterRepository characters;

    @Autowired
    TokenCipher cipher;

    @Autowired
    FakeEveSso sso;

    @BeforeEach
    void resetSso() {
        sso.reset();
    }

    private long link(Set<String> scopes) {
        var eveId = 2_000_000_000L + ThreadLocalRandom.current().nextInt(1_000_000_000);
        characters.link(viewerFor(newSub()).accountId(), eveId, "Alt One", "owner-hash-" + eveId, scopes,
                cipher.encrypt(eveId, "refresh-0"));
        return eveId;
    }

    private EveSso.RefreshedAccess answer(long eveId, Instant expiresAt, String refreshToken) {
        return sso.access(eveId, "owner-hash-" + eveId, BOTH, expiresAt, refreshToken);
    }

    @Test
    void refreshesOnceAndThenUsesTheTokenItHolds() {
        var eveId = link(BOTH);
        sso.nextRefresh = answer(eveId, Instant.now().plusSeconds(1200), "refresh-0");

        var first = tokens.accessToken(eveId, ASSETS);
        var second = tokens.accessToken(eveId, ASSETS);

        assertThat(first.problem()).isNull();
        assertThat(first.token()).isNotBlank();
        assertThat(second.token()).isEqualTo(first.token());
        assertThat(sso.refreshed).containsExactly("refresh-0");
    }

    @Test
    void refreshesWhenLessThanAMinuteIsLeftAndNotBefore() {
        var eveId = link(BOTH);
        sso.nextRefresh = answer(eveId, Instant.now().plusSeconds(30), "refresh-0");
        tokens.accessToken(eveId, ASSETS);

        sso.nextRefresh = answer(eveId, Instant.now().plusSeconds(1200), "refresh-0");
        tokens.accessToken(eveId, ASSETS);
        tokens.accessToken(eveId, ASSETS);

        // 30 s left is too little, so the second call refreshed again, and the third found 20 minutes
        assertThat(sso.refreshed).hasSize(2);
    }

    @Test
    void keepsTheNewestRefreshTokenAndEncryptsIt() {
        var eveId = link(BOTH);
        sso.nextRefresh = answer(eveId, Instant.now().plusSeconds(1200), "refresh-1");

        tokens.accessToken(eveId, ASSETS);

        assertThat(cipher.decrypt(eveId, characters.findToken(eveId).orElseThrow())).isEqualTo("refresh-1");
    }

    @Test
    void usesTheNewRefreshTokenForTheNextRefresh() {
        var eveId = link(BOTH);
        sso.nextRefresh = answer(eveId, Instant.now().plusSeconds(30), "refresh-1");
        tokens.accessToken(eveId, ASSETS);

        tokens.accessToken(eveId, ASSETS);

        assertThat(sso.refreshed).containsExactly("refresh-0", "refresh-1");
    }

    @Test
    void doesNotAskEveForAScopeTheCharacterDidNotGrant() {
        var eveId = link(Set.of("esi-universe.read_structures.v1"));

        var grant = tokens.accessToken(eveId, ASSETS);

        assertThat(grant.problem()).isEqualTo(TokenGrant.Problem.MISSING_SCOPE);
        assertThat(grant.token()).isNull();
        assertThat(sso.refreshed).isEmpty();
    }

    @Test
    void marksTheCharacterWhenEveRefusesTheSavedAccessAndNeverTriesAgain() {
        var eveId = link(BOTH);
        sso.refreshFailure = new EveSso.InvalidGrantException("refused", null);

        var first = tokens.accessToken(eveId, ASSETS);
        var second = tokens.accessToken(eveId, ASSETS);

        assertThat(first.problem()).isEqualTo(TokenGrant.Problem.NEEDS_REAUTHORIZATION);
        assertThat(second.problem()).isEqualTo(TokenGrant.Problem.NEEDS_REAUTHORIZATION);
        assertThat(sso.refreshed).hasSize(1);
        assertThat(characters.findStanding(eveId).orElseThrow().needsReauthorization()).isTrue();
    }

    @Test
    void marksTheCharacterWhenItsOwnerHasChanged() {
        var eveId = link(BOTH);
        sso.nextRefresh = sso.access(eveId, "someone-else", BOTH, Instant.now().plusSeconds(1200), "refresh-0");

        var grant = tokens.accessToken(eveId, ASSETS);

        assertThat(grant.problem()).isEqualTo(TokenGrant.Problem.NEEDS_REAUTHORIZATION);
        assertThat(grant.token()).isNull();
        assertThat(characters.findStanding(eveId).orElseThrow().needsReauthorization()).isTrue();
    }

    @Test
    void aFailureToReachEveIsTemporaryAndLeavesTheCharacterAlone() {
        var eveId = link(BOTH);
        sso.refreshFailure = new EveSso.SsoException("EVE SSO did not answer");

        var grant = tokens.accessToken(eveId, ASSETS);

        assertThat(grant.problem()).isEqualTo(TokenGrant.Problem.UNAVAILABLE);
        assertThat(characters.findStanding(eveId).orElseThrow().needsReauthorization()).isFalse();

        sso.refreshFailure = null;
        sso.nextRefresh = answer(eveId, Instant.now().plusSeconds(1200), "refresh-0");
        assertThat(tokens.accessToken(eveId, ASSETS).problem()).isNull();
    }

    @Test
    void anUnknownCharacterNeedsAuthorization() {
        assertThat(tokens.accessToken(1L, ASSETS).problem()).isEqualTo(TokenGrant.Problem.NEEDS_REAUTHORIZATION);
        assertThat(sso.refreshed).isEmpty();
    }

    @Test
    void forgettingATokenMakesTheNextCallRefresh() {
        var eveId = link(BOTH);
        sso.nextRefresh = answer(eveId, Instant.now().plusSeconds(1200), "refresh-0");
        tokens.accessToken(eveId, ASSETS);

        tokens.invalidate(eveId);
        tokens.accessToken(eveId, ASSETS);

        assertThat(sso.refreshed).hasSize(2);
    }

    @Test
    void severalRequestsForTheSameCharacterShareOneRefresh() throws Exception {
        var eveId = link(BOTH);
        sso.nextRefresh = answer(eveId, Instant.now().plusSeconds(1200), "refresh-0");
        var start = new CountDownLatch(1);
        List<TokenGrant> grants = java.util.Collections.synchronizedList(new ArrayList<>());
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 6; i++) {
                pool.submit(() -> {
                    start.await();
                    grants.add(tokens.accessToken(eveId, ASSETS));
                    return null;
                });
            }
            start.countDown();
        }

        assertThat(grants).hasSize(6).allSatisfy(grant -> assertThat(grant.problem()).isNull());
        assertThat(sso.refreshed).hasSize(1);
    }

    @Test
    void neverShowsATokenInATextForm() {
        var eveId = link(BOTH);
        sso.nextRefresh = answer(eveId, Instant.now().plusSeconds(1200), "refresh-0");

        var grant = tokens.accessToken(eveId, ASSETS);

        assertThat(grant.toString()).doesNotContain(grant.token());
        assertThat(sso.nextRefresh.toString()).doesNotContain(sso.nextRefresh.accessToken());
    }
}
