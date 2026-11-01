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
}
