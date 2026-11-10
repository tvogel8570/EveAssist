package com.eveassist.characters.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;

import com.eveassist.IntegrationTest;
import com.eveassist.characters.Feature;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * EVE may rotate refresh tokens without notice (its old documentation says the returned token "may not be the same"). A
 * newer token must then never be lost, even when it cannot be saved at once, because the saved one may already be dead.
 */
@Import(TestCharactersConfig.class)
class CharacterAccessTokensRotationIT extends IntegrationTest {

    private static final String ASSETS = "esi-assets.read_assets.v1";
    private static final Set<String> BOTH = Feature.INVENTORY.scopes();

    @Autowired
    CharacterAccessTokens tokens;

    @MockitoSpyBean
    CharacterRepository characters;

    @Autowired
    TokenCipher cipher;

    @Autowired
    FakeEveSso sso;

    @Autowired
    MeterRegistry meters;

    @BeforeEach
    void reset() {
        sso.reset();
        Mockito.reset(characters);
    }

    private long link() {
        var eveId = 2_000_000_000L + ThreadLocalRandom.current().nextInt(1_000_000_000);
        characters.link(viewerFor(newSub()).accountId(), eveId, "Alt One", "owner-hash-" + eveId, BOTH,
                cipher.encrypt(eveId, "refresh-0"));
        return eveId;
    }

    private String saved(long eveId) {
        return cipher.decrypt(eveId, characters.findToken(eveId).orElseThrow());
    }

    private double rotations() {
        return meters.get("eveassist.sso.refresh.rotated").counter().count();
    }

    @Test
    void aRotatedRefreshTokenIsCountedAndSaved() {
        var eveId = link();
        var before = rotations();
        sso.nextRefresh = sso.access(eveId, "owner-hash-" + eveId, BOTH, Instant.now().plusSeconds(1200), "refresh-1");

        var grant = tokens.accessToken(eveId, ASSETS);

        assertThat(grant.problem()).isNull();
        assertThat(saved(eveId)).isEqualTo("refresh-1");
        assertThat(rotations()).isEqualTo(before + 1);
    }

    @Test
    void aSameRefreshTokenIsNotCountedAsRotation() {
        var eveId = link();
        var before = rotations();
        sso.nextRefresh = sso.access(eveId, "owner-hash-" + eveId, BOTH, Instant.now().plusSeconds(1200), "refresh-0");

        tokens.accessToken(eveId, ASSETS);

        assertThat(rotations()).isEqualTo(before);
        assertThat(saved(eveId)).isEqualTo("refresh-0");
    }

    @Test
    void aNewTokenThatCannotBeSavedIsKeptAndSentAtTheNextRefresh() {
        var eveId = link();
        // an access token with 30 s left is refreshed again at the next call
        sso.nextRefresh = sso.access(eveId, "owner-hash-" + eveId, BOTH, Instant.now().plusSeconds(30), "refresh-1");
        doThrow(new DataAccessResourceFailureException("pool exhausted")).doCallRealMethod()
                .when(characters).replaceToken(anyLong(), any());

        var first = tokens.accessToken(eveId, ASSETS);

        // EVE gave a new token and saving failed: the caller still gets its access token, and the old one stays saved
        assertThat(first.problem()).isNull();
        assertThat(first.token()).isNotBlank();
        assertThat(saved(eveId)).isEqualTo("refresh-0");

        sso.nextRefresh = sso.access(eveId, "owner-hash-" + eveId, BOTH, Instant.now().plusSeconds(1200), "refresh-1");
        var second = tokens.accessToken(eveId, ASSETS);

        // the second refresh sent the kept token, not the dead saved one, and the newest is now saved
        assertThat(second.problem()).isNull();
        assertThat(sso.refreshed).containsExactly("refresh-0", "refresh-1");
        assertThat(saved(eveId)).isEqualTo("refresh-1");
    }

    @Test
    void aTokenThatCouldNotBeSavedIsSavedAtTheNextCallEvenWhenTheAccessTokenIsStillGood() {
        var eveId = link();
        sso.nextRefresh = sso.access(eveId, "owner-hash-" + eveId, BOTH, Instant.now().plusSeconds(1200), "refresh-1");
        doThrow(new DataAccessResourceFailureException("pool exhausted")).doCallRealMethod()
                .when(characters).replaceToken(anyLong(), any());
        tokens.accessToken(eveId, ASSETS);
        assertThat(saved(eveId)).isEqualTo("refresh-0");

        var second = tokens.accessToken(eveId, ASSETS);

        // no new call to EVE was needed: the cached access token served, and the kept refresh token was saved
        assertThat(second.problem()).isNull();
        assertThat(sso.refreshed).containsExactly("refresh-0");
        assertThat(saved(eveId)).isEqualTo("refresh-1");
    }

    @Test
    void aKeptTokenThatEveRefusesIsDroppedAndTheCharacterNeedsAuthorization() {
        var eveId = link();
        sso.nextRefresh = sso.access(eveId, "owner-hash-" + eveId, BOTH, Instant.now().plusSeconds(30), "refresh-1");
        doThrow(new DataAccessResourceFailureException("pool exhausted"))
                .when(characters).replaceToken(anyLong(), any());
        tokens.accessToken(eveId, ASSETS);
        sso.refreshFailure = new EveSso.InvalidGrantException("refused", null);

        var grant = tokens.accessToken(eveId, ASSETS);

        assertThat(grant.problem()).isEqualTo(com.eveassist.esi.TokenGrant.Problem.NEEDS_REAUTHORIZATION);
        // a later refresh does not resurrect the dropped token
        doCallRealMethod().when(characters).replaceToken(anyLong(), any());
        sso.refreshFailure = null;
        assertThat(tokens.accessToken(eveId, ASSETS).problem())
                .isEqualTo(com.eveassist.esi.TokenGrant.Problem.NEEDS_REAUTHORIZATION);
    }
}
