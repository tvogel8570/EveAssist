package com.eveassist;

import java.nio.file.Path;
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
    }

    private PostgresTestDatabase() {
    }

    public static void register(DynamicPropertyRegistry registry) {
        var url = "jdbc:postgresql://%s:%d/eveassist".formatted(
                CONTAINER.getHost(), CONTAINER.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT));
        registry.add("EVEASSIST_DB_URL", () -> url);
        registry.add("EVEASSIST_APP_PASSWORD", () -> "test-app");
        registry.add("EVEASSIST_OWNER_PASSWORD", () -> "test-owner");
    }
}
