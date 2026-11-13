package com.eveassist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Infrastructure/prod/support-roles.sql, run on the production migrations, and then used by logging in as each role. What a person with
 * support access may read and change is fixed here: the two roles cannot read the tokens or the sessions, support_ro cannot write at all,
 * and support_rw can write only the fix-up tables and only after asking for a read-write transaction.
 *
 * A table that a migration adds is neither readable nor excluded until someone decides, and this test fails until then: add it to the
 * allowlist in the script, or to EXCLUDED below.
 */
class SupportRolesIT {

    /** Tables that support must never read, on purpose: refresh tokens, and the session ids that are the login cookies. */
    private static final Set<String> EXCLUDED = Set.of("character_token", "spring_session", "spring_session_attributes");

    private static final String DATABASE = "support_roles";
    private static final String PERMISSION_DENIED = "42501";
    private static final String READ_ONLY_TRANSACTION = "25006";

    @BeforeAll
    static void migrateAndRunTheScriptTwice() throws Exception {
        var url = PostgresTestDatabase.createEmptyDatabase(DATABASE);
        Flyway.configure().dataSource(url, "eveassist_owner", "test-owner").locations("classpath:db/migration").load().migrate();
        try (var connection = PostgresTestDatabase.superuser(DATABASE); var statement = connection.createStatement()) {
            // the SDE schema, as production has it: owned by its own role, public game data
            statement.execute("CREATE SCHEMA sde AUTHORIZATION eveassist_sde");
            statement.execute("SET ROLE eveassist_sde");
            statement.execute("CREATE TABLE sde.example (id integer PRIMARY KEY, name text)");
            statement.execute("INSERT INTO sde.example VALUES (34, 'Tritanium')");
            statement.execute("RESET ROLE");
        }
        var script = Path.of("../Infrastructure/prod/support-roles.sql");
        PostgresTestDatabase.runPsqlScript(DATABASE, script);
        PostgresTestDatabase.runPsqlScript(DATABASE, script);   // safe to run again
        try (var connection = PostgresTestDatabase.superuser(DATABASE); var statement = connection.createStatement()) {
            // production logs in with an IAM token; here, a password stands in for it
            statement.execute("ALTER ROLE support_ro PASSWORD 'ro-test'");
            statement.execute("ALTER ROLE support_rw PASSWORD 'rw-test'");
            statement.execute("INSERT INTO esi_cache (cache_key, etag, expires, fetched_at) VALUES ('characters/1/assets', 'e', now(), now())");
        }
    }

    private static Connection asReadOnly() throws SQLException {
        return DriverManager.getConnection(PostgresTestDatabase.jdbcUrl(DATABASE), "support_ro", "ro-test");
    }

    private static Connection asReadWrite() throws SQLException {
        return DriverManager.getConnection(PostgresTestDatabase.jdbcUrl(DATABASE), "support_rw", "rw-test");
    }

    /** The SQLSTATE of the refusal. Fails with a clear message when the statement was not refused at all. */
    private static String sqlState(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        var thrown = org.assertj.core.api.Assertions.catchThrowable(action);
        assertThat(thrown).as("the statement should have been refused, but it ran").isInstanceOf(SQLException.class);
        return ((SQLException) thrown).getSQLState();
    }

    @Test
    void everyTableIsEitherReadableOrExcludedOnPurpose() throws Exception {
        var unreadable = new HashSet<String>();
        var readable = new HashSet<String>();
        try (var connection = PostgresTestDatabase.superuser(DATABASE); var statement = connection.createStatement();
                var tables = statement.executeQuery("""
                        SELECT table_name, has_table_privilege('support_ro', format('public.%I', table_name), 'SELECT')
                        FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE'""")) {
            while (tables.next()) {
                (tables.getBoolean(2) ? readable : unreadable).add(tables.getString(1));
            }
        }

        assertThat(unreadable)
                .as("Tables support cannot read. A table that is not in EXCLUDED needs a decision: add it to support-roles.sql, or to EXCLUDED")
                .isEqualTo(EXCLUDED);
        assertThat(readable).as("readable tables").doesNotContainAnyElementsOf(EXCLUDED).isNotEmpty();
    }

    @Test
    void aTableAddedByALaterMigrationIsNotReadableUntilSomeoneDecides() throws Exception {
        try (var connection = PostgresTestDatabase.superuser(DATABASE); var statement = connection.createStatement()) {
            statement.execute("SET ROLE eveassist_owner");
            statement.execute("CREATE TABLE zz_new_table (id integer)");
            statement.execute("RESET ROLE");
        }
        try (var connection = asReadOnly(); var statement = connection.createStatement()) {
            assertThat(sqlState(() -> statement.executeQuery("SELECT * FROM zz_new_table"))).isEqualTo(PERMISSION_DENIED);
        } finally {
            try (var connection = PostgresTestDatabase.superuser(DATABASE); var statement = connection.createStatement()) {
                statement.execute("DROP TABLE zz_new_table");
            }
        }
    }

    @Test
    void supportRoReadsTheAllowlistAndTheSde() throws Exception {
        try (var connection = asReadOnly(); var statement = connection.createStatement()) {
            assertThat(statement.executeQuery("SELECT count(*) FROM account").next()).isTrue();
            assertThat(statement.executeQuery("SELECT count(*) FROM flyway_schema_history").next()).isTrue();
            var sde = statement.executeQuery("SELECT name FROM sde.example");
            assertThat(sde.next()).isTrue();
            assertThat(sde.getString(1)).isEqualTo("Tritanium");
        }
    }

    @Test
    void neitherRoleReadsTheTokensOrTheSessions() throws Exception {
        for (var connection : new Connection[] {asReadOnly(), asReadWrite()}) {
            try (connection; var statement = connection.createStatement()) {
                for (var table : EXCLUDED) {
                    assertThat(sqlState(() -> statement.executeQuery("SELECT * FROM " + table))).as(table).isEqualTo(PERMISSION_DENIED);
                }
            }
        }
    }

    @Test
    void supportRoCannotWriteEvenWhenAskingForAReadWriteTransaction() throws Exception {
        try (var connection = asReadOnly(); var statement = connection.createStatement()) {
            assertThat(statement.executeQuery("SHOW default_transaction_read_only").next()).isTrue();
            assertThat(sqlState(() -> statement.execute("DELETE FROM esi_cache"))).isEqualTo(READ_ONLY_TRANSACTION);
            connection.setAutoCommit(false);
            statement.execute("SET TRANSACTION READ WRITE");
            assertThat(sqlState(() -> statement.execute("DELETE FROM esi_cache"))).isEqualTo(PERMISSION_DENIED);
            connection.rollback();
        }
    }

    @Test
    void supportRwIsReadOnlyUntilItAsksForReadWrite() throws Exception {
        try (var connection = asReadWrite(); var statement = connection.createStatement()) {
            assertThat(sqlState(() -> statement.execute("DELETE FROM esi_cache"))).as("a plain statement").isEqualTo(READ_ONLY_TRANSACTION);
            connection.setAutoCommit(false);
            statement.execute("SET TRANSACTION READ WRITE");
            assertThat(statement.executeUpdate("DELETE FROM esi_cache WHERE cache_key = 'characters/1/assets'")).isEqualTo(1);
            connection.rollback();   // the row is back: a fix-up is a transaction that can be abandoned
            statement.execute("SELECT 1");
            assertThat(statement.executeQuery("SELECT count(*) FROM esi_cache WHERE cache_key = 'characters/1/assets'").next()).isTrue();
        }
    }

    @Test
    void supportRwWritesOnlyTheFixUpTables() throws Exception {
        try (var connection = asReadWrite(); var statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.execute("SET TRANSACTION READ WRITE");
            for (var forbidden : new String[] {"DELETE FROM account", "DELETE FROM plan", "DELETE FROM character", "DELETE FROM corporation",
                    "UPDATE account SET created_at = now()", "UPDATE plan SET name = 'x'", "INSERT INTO esi_cache (cache_key, expires, fetched_at) VALUES ('x', now(), now())",
                    "DELETE FROM flyway_schema_history", "DROP TABLE esi_cache", "TRUNCATE esi_cache", "CREATE TABLE public.mine (id integer)"}) {
                assertThat(sqlState(() -> statement.execute(forbidden))).as(forbidden).isEqualTo(PERMISSION_DENIED);
                connection.rollback();
                statement.execute("SET TRANSACTION READ WRITE");
            }
            connection.rollback();
        }
    }

    @Test
    void theSdeIsReadOnlyForSupport() throws Exception {
        try (var connection = asReadWrite(); var statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.execute("SET TRANSACTION READ WRITE");
            assertThat(sqlState(() -> statement.execute("DELETE FROM sde.example"))).isEqualTo(PERMISSION_DENIED);
            connection.rollback();
        }
    }

    @Test
    void theRolesAreOrdinaryLoginRolesWithSafetyNets() throws Exception {
        try (var connection = PostgresTestDatabase.superuser(DATABASE); var statement = connection.createStatement();
                var roles = statement.executeQuery("""
                        SELECT rolname, rolsuper, rolcreaterole, rolcreatedb, rolcanlogin, rolreplication, rolbypassrls, rolconnlimit,
                               (SELECT string_agg(c, ',' ORDER BY c) FROM unnest(rolconfig) c)
                        FROM pg_roles WHERE rolname IN ('support_ro', 'support_rw') ORDER BY rolname""")) {
            var seen = 0;
            while (roles.next()) {
                seen++;
                var name = roles.getString(1);
                assertThat(roles.getBoolean(2)).as(name + " superuser").isFalse();
                assertThat(roles.getBoolean(3)).as(name + " createrole").isFalse();
                assertThat(roles.getBoolean(4)).as(name + " createdb").isFalse();
                assertThat(roles.getBoolean(5)).as(name + " login").isTrue();
                assertThat(roles.getBoolean(6)).as(name + " replication").isFalse();
                assertThat(roles.getBoolean(7)).as(name + " bypassrls").isFalse();
                assertThat(roles.getInt(8)).as(name + " connection limit").isPositive();
                assertThat(roles.getString(9)).as(name + " settings")
                        .contains("default_transaction_read_only=on", "statement_timeout=60s", "idle_in_transaction_session_timeout=");
            }
            assertThat(seen).isEqualTo(2);
        }
    }

    @Test
    void neitherRoleHasKeycloakOrOwnerPrivileges() throws Exception {
        try (var connection = PostgresTestDatabase.superuser(DATABASE); var statement = connection.createStatement();
                var rows = statement.executeQuery("""
                        SELECT count(*) FROM pg_auth_members m JOIN pg_roles member ON member.oid = m.member JOIN pg_roles r ON r.oid = m.roleid
                        WHERE member.rolname IN ('support_ro', 'support_rw')
                          AND r.rolname IN ('eveassist_owner', 'eveassist_sde', 'eveassist_app', 'keycloak_owner', 'postgres')""")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getInt(1)).isZero();
        }
        assertThatThrownBy(() -> {
            try (var connection = DriverManager.getConnection(PostgresTestDatabase.jdbcUrl("keycloak"), "support_ro", "ro-test")) {
                connection.createStatement().executeQuery("SELECT 1");
            }
        }).isInstanceOf(SQLException.class);
    }
}
