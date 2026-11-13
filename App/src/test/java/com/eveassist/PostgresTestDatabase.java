package com.eveassist;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Postgres set up by the same script as the real one, so that tests run as the app's own limited role and migrations
 * as its owner. One container serves the whole test run.
 */
public final class PostgresTestDatabase {

    private static final PostgreSQLContainer CONTAINER = new PostgreSQLContainer("postgres:18.6-alpine3.24")
            .withEnv("KC_DB_PASSWORD", "test-keycloak")
            .withEnv("EVEASSIST_OWNER_PASSWORD", "test-owner")
            .withEnv("EVEASSIST_APP_PASSWORD", "test-app")
            .withEnv("EVEASSIST_SDE_PASSWORD", "test-sde")
            .withUsername("postgres")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(Path.of("../Infrastructure/postgres/sql/init/3-init-eveassist.sh"), 0755),
                    "/docker-entrypoint-initdb.d/3-init-eveassist.sh");

    static {
        CONTAINER.start();
        loadSdeFixture();
    }

    /** The real SDE is far too big for tests, so this is a small one with the same table names. */
    private static void loadSdeFixture() {
        try (var connection = DriverManager.getConnection(url(), "eveassist_sde", "test-sde");
                var statement = connection.createStatement();
                var script = PostgresTestDatabase.class.getResourceAsStream("/sde-fixture.sql")) {
            statement.execute(new String(script.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException | SQLException e) {
            throw new IllegalStateException("Could not load the SDE fixture", e);
        }
    }

    /** A new, empty database owned by the migrations' role, for tests that need a schema nothing else has touched. */
    public static String createEmptyDatabase(String name) {
        var admin = "jdbc:postgresql://%s:%d/postgres".formatted(
                CONTAINER.getHost(), CONTAINER.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT));
        try (var connection = DriverManager.getConnection(admin, CONTAINER.getUsername(), CONTAINER.getPassword());
                var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE \"" + name + "\" OWNER eveassist_owner");
        } catch (SQLException e) {
            throw new IllegalStateException("Could not create the database " + name, e);
        }
        return "jdbc:postgresql://%s:%d/%s".formatted(
                CONTAINER.getHost(), CONTAINER.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT), name);
    }

    /** The JDBC address of a database in the shared container. */
    public static String jdbcUrl(String database) {
        return "jdbc:postgresql://%s:%d/%s".formatted(
                CONTAINER.getHost(), CONTAINER.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT), database);
    }

    /** A connection as the container's superuser, for set-up that no limited role may do. */
    public static java.sql.Connection superuser(String database) throws SQLException {
        return DriverManager.getConnection(jdbcUrl(database), CONTAINER.getUsername(), CONTAINER.getPassword());
    }

    /**
     * Runs a SQL file with psql inside the container, as the superuser. For scripts that use psql's own commands (\gexec, \set),
     * which JDBC cannot run. Fails when psql does.
     */
    public static String runPsqlScript(String database, Path script) {
        var target = "/tmp/" + script.getFileName();
        try {
            CONTAINER.copyFileToContainer(MountableFile.forHostPath(script), target);
            var result = CONTAINER.execInContainer("psql", "-U", CONTAINER.getUsername(), "-d", database, "-v", "ON_ERROR_STOP=1", "-f", target);
            if (result.getExitCode() != 0) {
                throw new IllegalStateException("psql failed on " + script + ": " + result.getStderr());
            }
            return result.getStdout() + result.getStderr();
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException("Could not run " + script, e);
        }
    }

    private static String url() {
        return "jdbc:postgresql://%s:%d/eveassist".formatted(
                CONTAINER.getHost(), CONTAINER.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT));
    }

    private PostgresTestDatabase() {
    }

    public static void register(DynamicPropertyRegistry registry) {
        registry.add("EVEASSIST_DB_URL", PostgresTestDatabase::url);
        registry.add("EVEASSIST_APP_PASSWORD", () -> "test-app");
        registry.add("EVEASSIST_OWNER_PASSWORD", () -> "test-owner");
    }
}
