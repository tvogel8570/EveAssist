package com.eveassist.characters.internal;

import com.eveassist.identity.AccountId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class CharacterRepository {

    enum LinkOutcome { LINKED, RENEWED, ON_ANOTHER_ACCOUNT }

    private final JdbcClient jdbc;

    CharacterRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Links a character to the account, or renews its token and scopes when it is already on that account. A character
     * on another account is left alone.
     */
    @Transactional
    LinkOutcome link(AccountId owner, long eveCharacterId, String name, String ownerHash, Set<String> scopes,
            EncryptedToken token) {
        var existingOwner = jdbc.sql("SELECT owner_account_id FROM character WHERE eve_character_id = :eve FOR UPDATE")
                .param("eve", eveCharacterId)
                .query(UUID.class)
                .optional();
        if (existingOwner.isPresent() && !existingOwner.get().equals(owner.value())) {
            return LinkOutcome.ON_ANOTHER_ACCOUNT;
        }
        var scopeArray = scopes.stream().sorted().toArray(String[]::new);
        Long id;
        LinkOutcome outcome;
        if (existingOwner.isPresent()) {
            id = jdbc.sql("""
                    UPDATE character SET name = :name, owner_hash = :hash, scopes = :scopes, state = 'ACTIVE'
                    WHERE eve_character_id = :eve RETURNING id
                    """)
                    .param("name", name).param("hash", ownerHash).param("scopes", scopeArray)
                    .param("eve", eveCharacterId)
                    .query(Long.class).single();
            outcome = LinkOutcome.RENEWED;
        } else {
            id = jdbc.sql("""
                    INSERT INTO character (public_id, owner_account_id, eve_character_id, name, owner_hash, scopes)
                    VALUES (:publicId, :owner, :eve, :name, :hash, :scopes) RETURNING id
                    """)
                    .param("publicId", UUID.randomUUID()).param("owner", owner.value())
                    .param("eve", eveCharacterId).param("name", name).param("hash", ownerHash)
                    .param("scopes", scopeArray)
                    .query(Long.class).single();
            outcome = LinkOutcome.LINKED;
        }
        storeToken(id, token);
        return outcome;
    }

    /** The newest refresh token replaces the stored one. */
    void storeToken(long characterId, EncryptedToken token) {
        jdbc.sql("""
                INSERT INTO character_token (character_id, ciphertext, nonce, key_id)
                VALUES (:id, :ciphertext, :nonce, :keyId)
                ON CONFLICT (character_id) DO UPDATE
                SET ciphertext = :ciphertext, nonce = :nonce, key_id = :keyId, updated_at = now()
                """)
                .param("id", characterId).param("ciphertext", token.ciphertext()).param("nonce", token.nonce())
                .param("keyId", token.keyId())
                .update();
    }

    Optional<EncryptedToken> findToken(long eveCharacterId) {
        return jdbc.sql("""
                SELECT t.ciphertext, t.nonce, t.key_id FROM character_token t
                JOIN character c ON c.id = t.character_id WHERE c.eve_character_id = :eve
                """)
                .param("eve", eveCharacterId)
                .query((rs, row) -> new EncryptedToken(rs.getBytes("ciphertext"), rs.getBytes("nonce"),
                        rs.getString("key_id")))
                .optional();
    }

    List<CharacterDetails> listFor(AccountId owner) {
        return jdbc.sql("""
                SELECT public_id, eve_character_id, name, scopes, state, portrait IS NOT NULL AS has_portrait FROM character
                WHERE owner_account_id = :owner ORDER BY name
                """)
                .param("owner", owner.value())
                .query((rs, row) -> new CharacterDetails(rs.getObject("public_id", UUID.class),
                        rs.getLong("eve_character_id"), rs.getString("name"),
                        Set.of((String[]) rs.getArray("scopes").getArray()), rs.getString("state"),
                        rs.getBoolean("has_portrait")))
                .list();
    }

    void storePortrait(long eveCharacterId, byte[] portrait) {
        jdbc.sql("UPDATE character SET portrait = :portrait, portrait_fetched_at = now() WHERE eve_character_id = :eve")
                .param("portrait", portrait).param("eve", eveCharacterId)
                .update();
    }

    /** Only the owner gets the portrait. */
    Optional<byte[]> findPortrait(AccountId owner, UUID publicId) {
        return jdbc.sql("SELECT portrait FROM character WHERE public_id = :id AND owner_account_id = :owner "
                        + "AND portrait IS NOT NULL")
                .param("id", publicId).param("owner", owner.value())
                .query((rs, row) -> rs.getBytes("portrait"))
                .optional();
    }

    /** Deletes the character with its token (and, later, its ESI data) at once. */
    boolean remove(AccountId owner, UUID publicId) {
        return jdbc.sql("DELETE FROM character WHERE public_id = :id AND owner_account_id = :owner")
                .param("id", publicId).param("owner", owner.value())
                .update() > 0;
    }
}
