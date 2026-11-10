package com.eveassist.characters.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.eveassist.IntegrationTest;
import com.eveassist.characters.Feature;
import com.eveassist.esi.TokenGrant;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * Many characters needing a fresh access token at once, as after a restart, with a pool of database connections that is
 * small beside them. A refresh holds a connection for its lock and needs another for its own queries, so unlimited
 * refreshes would each hold one and wait for a second until the pool timed out (seen with 20 characters and a pool of
 * 10). The pool here is 4 and the wait for a connection 3 s, so that the failure shows quickly.
 */
@Import(TestCharactersConfig.class)
@TestPropertySource(properties = {"spring.datasource.hikari.maximum-pool-size=4",
        "spring.datasource.hikari.connection-timeout=3000"})
class CharacterAccessTokensPoolIT extends IntegrationTest {

    @Autowired
    CharacterAccessTokens tokens;

    @Autowired
    CharacterRepository characters;

    @Autowired
    TokenCipher cipher;

    @Autowired
    FakeEveSso sso;

    @Test
    void manyCharactersRefreshingAtOnceNeverUseUpThePool() throws Exception {
        sso.reset();
        var account = viewerFor(newSub()).accountId();
        var scopes = Feature.INVENTORY.scopes();
        var ids = new ArrayList<Long>();
        for (int i = 0; i < 20; i++) {
            var eveId = 2_000_000_000L + ThreadLocalRandom.current().nextInt(1_000_000_000);
            characters.link(account, eveId, "Alt " + i, "owner-hash-" + eveId, scopes,
                    cipher.encrypt(eveId, "refresh-" + eveId));
            sso.answers.put("refresh-" + eveId, sso.access(eveId, "owner-hash-" + eveId, scopes,
                    Instant.now().plusSeconds(1200), "refresh-" + eveId));
            ids.add(eveId);
        }

        List<TokenGrant> grants;
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = ids.stream()
                    .map(id -> pool.submit(() -> tokens.accessToken(id, "esi-assets.read_assets.v1"))).toList();
            grants = new ArrayList<>();
            for (var future : futures) {
                grants.add(future.get());
            }
        }

        assertThat(grants).hasSize(20).allSatisfy(grant -> assertThat(grant.problem()).isNull());
    }
}
