package com.eveassist.identity.internal;

import com.eveassist.identity.AccountId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class AccountRepository {

    private final JdbcClient jdbc;

    AccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** The display name is kept from the first sign-in, because the user may change it later. */
    void insertIfAbsent(String keycloakSub, String displayName) {
        jdbc.sql("""
                INSERT INTO account (public_id, keycloak_sub, display_name)
                VALUES (:publicId, :sub, :displayName)
                ON CONFLICT (keycloak_sub) DO NOTHING
                """)
                .param("publicId", UUID.randomUUID())
                .param("sub", keycloakSub)
                .param("displayName", displayName)
                .update();
    }

    Optional<AccountDetails> findByKeycloakSub(String keycloakSub) {
        return jdbc.sql("SELECT public_id, display_name FROM account WHERE keycloak_sub = :sub")
                .param("sub", keycloakSub)
                .query((rs, row) -> new AccountDetails(
                        new AccountId(rs.getObject("public_id", UUID.class)), rs.getString("display_name")))
                .optional();
    }

    /**
     * Deletes the account. The database removes everything that belongs to it (characters and their tokens and data,
     * features, plans, corporations) because each of those tables references the account with ON DELETE CASCADE.
     */
    void delete(AccountId id) {
        jdbc.sql("DELETE FROM account WHERE public_id = :id").param("id", id.value()).update();
    }

    /** Signs the user out everywhere. A session's principal name is the Keycloak subject (user-name-attribute: sub). */
    void deleteSessions(String keycloakSub) {
        jdbc.sql("DELETE FROM spring_session WHERE principal_name = :sub").param("sub", keycloakSub).update();
    }
}
