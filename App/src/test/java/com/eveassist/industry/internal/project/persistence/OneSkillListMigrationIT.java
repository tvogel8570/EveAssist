package com.eveassist.industry.internal.project.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.eveassist.PostgresTestDatabase;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

/**
 * The migration that moves a builder's skill levels from the items to the project, run over rows stored the old way. Every other test starts from an
 * empty database, where it has nothing to move.
 */
class OneSkillListMigrationIT {

    private static final String DATABASE = "one_skill_list";

    private static Flyway flyway(String url, String target) {
        var configured = Flyway.configure().dataSource(url, "eveassist_owner", "test-owner").locations("classpath:db/migration");
        return (target == null ? configured : configured.target(target)).load();
    }

    @Test
    void eachSkillOfAProjectBecomesOneRowAtTheHighestLevelItsItemsHad() throws SQLException {
        var url = PostgresTestDatabase.createEmptyDatabase(DATABASE);
        // everything up to the migration before the one under test
        flyway(url, "20261125.2303").migrate();
        try (var connection = PostgresTestDatabase.superuser(DATABASE); var statement = connection.createStatement()) {
            statement.execute("INSERT INTO account (public_id, keycloak_sub, display_name) VALUES ('00000000-0000-0000-0000-000000000001', 'skills', 'Skills')");
            var first = project(statement, "00000000-0000-0000-0000-00000000000a");
            var second = project(statement, "00000000-0000-0000-0000-00000000000b");
            // two items of the first project disagree about skill 11442, and the second project holds its own level of it
            skill(statement, first, 1001, 11442, 2);
            skill(statement, first, 1002, 11442, 4);
            skill(statement, first, 1002, 11452, 1);
            skill(statement, second, 1001, 11442, 5);

            flyway(url, null).migrate();

            var rows = new ArrayList<String>();
            try (var found = statement.executeQuery("SELECT project_id, skill_type_id, level FROM project_skill ORDER BY 1, 2")) {
                while (found.next()) {
                    rows.add(found.getLong(1) + " " + found.getInt(2) + " " + found.getInt(3));
                }
            }
            assertThat(rows).containsExactly(first + " 11442 4", first + " 11452 1", second + " 11442 5");
            try (var found = statement.executeQuery("SELECT to_regclass('project_item_skill') IS NULL")) {
                found.next();
                assertThat(found.getBoolean(1)).isTrue();
            }
        }
    }

    private static long project(Statement statement, String id) throws SQLException {
        try (var made = statement.executeQuery("INSERT INTO project (public_id, owner_account_id, name) VALUES ('" + id
                + "', '00000000-0000-0000-0000-000000000001', 'P') RETURNING id")) {
            made.next();
            return made.getLong(1);
        }
    }

    private static void skill(Statement statement, long project, int item, int skill, int level) throws SQLException {
        statement.execute("INSERT INTO project_item (project_id, type_id) VALUES (" + project + ", " + item + ") ON CONFLICT DO NOTHING");
        statement.execute("INSERT INTO project_item_skill (project_id, item_type_id, skill_type_id, level) VALUES (" + project + ", " + item + ", "
                + skill + ", " + level + ")");
    }
}
