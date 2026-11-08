package com.eveassist;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

/**
 * The production migrations alone, on an empty database, as the migrations' own role. A migration that only works with
 * test data, or that Flyway would refuse at start-up, fails here.
 */
class MigrationsIT {

    @Test
    void productionMigrationsApplyToAnEmptyDatabaseAndValidate() {
        var flyway = Flyway.configure()
                .dataSource(PostgresTestDatabase.createEmptyDatabase("migrations_only"), "eveassist_owner", "test-owner")
                .locations("classpath:db/migration")
                .outOfOrder(false)
                .load();

        var result = flyway.migrate();

        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isGreaterThanOrEqualTo(6);
        flyway.validate();
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }
}
