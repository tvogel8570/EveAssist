package com.eveassist.characters.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eveassist.IntegrationTest;
import com.eveassist.characters.Feature;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

class CharacterRepositoryIT extends IntegrationTest {

    @Autowired
    CharacterRepository characters;

    @Autowired
    TokenCipher cipher;

    @Autowired
    JdbcClient jdbc;

    private static long newEveId() {
        return 2_000_000_000L + ThreadLocalRandom.current().nextInt(1_000_000_000);
    }

    @Test
    void linksACharacterAndStoresAnEncryptedTokenThatDecrypts() {
        var owner = viewerFor(newSub()).accountId();
        var eveId = newEveId();

        var outcome = characters.link(owner, eveId, "Alt One", "hash-1", Feature.INVENTORY.scopes(),
                cipher.encrypt(eveId, "refresh-1"));

        assertThat(outcome).isEqualTo(CharacterRepository.LinkOutcome.LINKED);
        assertThat(characters.listFor(owner)).singleElement().satisfies(c -> {
            assertThat(c.name()).isEqualTo("Alt One");
            assertThat(c.scopes()).isEqualTo(Feature.INVENTORY.scopes());
            assertThat(c.state()).isEqualTo("ACTIVE");
        });
        assertThat(cipher.decrypt(eveId, characters.findToken(eveId).orElseThrow())).isEqualTo("refresh-1");
    }

    @Test
    void linkingAgainRenewsTheTokenAndScopes() {
        var owner = viewerFor(newSub()).accountId();
        var eveId = newEveId();
        characters.link(owner, eveId, "Alt One", "hash-1", Set.of("a"), cipher.encrypt(eveId, "old"));

        var outcome = characters.link(owner, eveId, "Alt One", "hash-1", Set.of("a", "b"),
                cipher.encrypt(eveId, "new"));

        assertThat(outcome).isEqualTo(CharacterRepository.LinkOutcome.RENEWED);
        assertThat(characters.listFor(owner)).singleElement().satisfies(c -> assertThat(c.scopes()).containsExactlyInAnyOrder("a", "b"));
        assertThat(cipher.decrypt(eveId, characters.findToken(eveId).orElseThrow())).isEqualTo("new");
    }

    @Test
    void aCharacterOnAnotherAccountIsRefusedAndLeftAlone() {
        var first = viewerFor(newSub()).accountId();
        var second = viewerFor(newSub()).accountId();
        var eveId = newEveId();
        characters.link(first, eveId, "Alt One", "hash-1", Set.of("a"), cipher.encrypt(eveId, "mine"));

        var outcome = characters.link(second, eveId, "Alt One", "hash-2", Set.of("a"), cipher.encrypt(eveId, "theirs"));

        assertThat(outcome).isEqualTo(CharacterRepository.LinkOutcome.ON_ANOTHER_ACCOUNT);
        assertThat(characters.listFor(second)).isEmpty();
        assertThat(cipher.decrypt(eveId, characters.findToken(eveId).orElseThrow())).isEqualTo("mine");
    }

    @Test
    void removingACharacterDeletesItsTokenAtOnce() {
        var owner = viewerFor(newSub()).accountId();
        var eveId = newEveId();
        characters.link(owner, eveId, "Alt One", "hash-1", Set.of("a"), cipher.encrypt(eveId, "refresh"));
        var id = characters.listFor(owner).getFirst().id();

        assertThat(characters.remove(viewerFor(newSub()).accountId(), id)).isFalse();
        assertThat(characters.remove(owner, id)).isTrue();

        assertThat(characters.listFor(owner)).isEmpty();
        assertThat(characters.findToken(eveId)).isEmpty();
    }

    @Test
    void theDatabaseRejectsAnUnknownStateAndAShortNonce() {
        var owner = viewerFor(newSub()).accountId();
        var eveId = newEveId();
        characters.link(owner, eveId, "Alt One", "hash-1", Set.of("a"), cipher.encrypt(eveId, "refresh"));

        assertThatThrownBy(() -> jdbc.sql("UPDATE character SET state = 'BROKEN' WHERE eve_character_id = :eve")
                .param("eve", eveId).update()).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.sql("""
                UPDATE character_token SET nonce = '\\x0102'::bytea
                WHERE character_id = (SELECT id FROM character WHERE eve_character_id = :eve)
                """).param("eve", eveId).update()).isInstanceOf(DataIntegrityViolationException.class);
    }
}
