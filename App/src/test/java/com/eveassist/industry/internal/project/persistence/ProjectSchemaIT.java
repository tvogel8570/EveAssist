package com.eveassist.industry.internal.project.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eveassist.IntegrationTest;
import com.eveassist.industry.internal.plan.application.PlanRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * What the project tables enforce on their own, before any repository uses them (architecture/projects.md): who owns what and what
 * a delete takes with it, the checks, the one Draft costing, the quote that cannot be updated, and a costing that must agree with
 * its quote. Each test makes its own account, so none depends on another or on empty tables.
 */
class ProjectSchemaIT extends IntegrationTest {

    @Autowired
    JdbcClient jdbc;

    @Autowired
    PlanRepository plans;

    private UUID newAccount() {
        return viewerFor(newSub()).accountId().value();
    }

    private long project(UUID account) {
        return jdbc.sql("INSERT INTO project (public_id, owner_account_id, name) VALUES (:id, :owner, 'P') RETURNING id")
                .param("id", UUID.randomUUID()).param("owner", account).query(Long.class).single();
    }

    private long facility(UUID account) {
        return jdbc.sql("INSERT INTO facility (public_id, owner_account_id, name) VALUES (:id, :owner, 'Azbel') RETURNING id")
                .param("id", UUID.randomUUID()).param("owner", account).query(Long.class).single();
    }

    private long quote(long project, int number) {
        return jdbc.sql("""
                INSERT INTO project_quote (project_id, number, mode, price, inputs_format_version, inputs)
                VALUES (:project, :number, 'margin', 1000, 1, '{}'::jsonb) RETURNING id""")
                .param("project", project).param("number", number).query(Long.class).single();
    }

    private long costing(long project, Long quote) {
        return jdbc.sql("""
                INSERT INTO project_costing (project_id, quote_id, costed_project_version, costed_sde_version, delivered_cost,
                                             quotable, document_format_version, document)
                VALUES (:project, :quote, 0, 'sde', 500, true, 1, '{}'::jsonb) RETURNING id""")
                .param("project", project).param("quote", quote).query(Long.class).single();
    }

    private void trace(long costing) {
        jdbc.sql("INSERT INTO project_trace (costing_id, document_format_version, document) VALUES (:c, 1, '{}'::jsonb)")
                .param("c", costing).update();
    }

    private int count(String table, String where, Object value) {
        return jdbc.sql("SELECT count(*) FROM " + table + " WHERE " + where + " = :v").param("v", value)
                .query(Integer.class).single();
    }

    @Test
    void deletingAnAccountRemovesItsProjectsFacilitiesAndEverythingUnderThem() {
        var account = newAccount();
        var facility = facility(account);
        var project = project(account);
        jdbc.sql("INSERT INTO project_product (project_id, type_id, quantity) VALUES (:p, 1001, 2)").param("p", project).update();
        jdbc.sql("INSERT INTO project_bought_part (project_id, type_id) VALUES (:p, 1002)").param("p", project).update();
        jdbc.sql("INSERT INTO project_item (project_id, type_id, facility_id) VALUES (:p, 1001, :f)")
                .param("p", project).param("f", facility).update();
        jdbc.sql("INSERT INTO project_skill (project_id, skill_type_id, level) VALUES (:p, 11442, 5)")
                .param("p", project).update();
        jdbc.sql("INSERT INTO project_price (project_id, type_id, as_of) VALUES (:p, 34, now())").param("p", project).update();
        var quote = quote(project, 1);
        trace(costing(project, quote));
        trace(costing(project, null));

        jdbc.sql("DELETE FROM account WHERE public_id = :a").param("a", account).update();

        for (var table : new String[] {"project_product", "project_bought_part", "project_item", "project_skill",
                "project_price", "project_quote", "project_costing"}) {
            assertThat(count(table, "project_id", project)).as(table).isZero();
        }
        assertThat(count("project", "id", project)).isZero();
        assertThat(count("facility", "id", facility)).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM project_trace t JOIN project_costing c ON c.id = t.costing_id WHERE c.project_id = :p")
                .param("p", project).query(Integer.class).single()).isZero();
    }

    @Test
    void deletingThePlanLeavesTheProjectItWasStartedFrom() {
        var owner = viewerFor(newSub());
        var account = owner.accountId().value();
        var planId = plans.create(owner, "Source");
        var planKey = jdbc.sql("SELECT id FROM plan WHERE public_id = :p").param("p", planId.value()).query(Long.class).single();
        var project = jdbc.sql("INSERT INTO project (public_id, owner_account_id, name, source_plan_id) VALUES (:id, :o, 'P', :plan) RETURNING id")
                .param("id", UUID.randomUUID()).param("o", account).param("plan", planKey).query(Long.class).single();

        plans.delete(owner, planId);

        assertThat(count("project", "id", project)).isOne();
        assertThat(jdbc.sql("SELECT count(*) FROM project WHERE id = :p AND source_plan_id IS NULL").param("p", project)
                .query(Integer.class).single()).isOne();
    }

    @Test
    void deletingAFacilityFallsBackToTheProjectDefault() {
        var account = newAccount();
        var facility = facility(account);
        var project = project(account);
        jdbc.sql("UPDATE project SET default_facility_id = :f WHERE id = :p").param("f", facility).param("p", project).update();
        jdbc.sql("INSERT INTO project_item (project_id, type_id, facility_id) VALUES (:p, 1001, :f)")
                .param("p", project).param("f", facility).update();

        jdbc.sql("DELETE FROM facility WHERE id = :f").param("f", facility).update();

        assertThat(jdbc.sql("SELECT count(*) FROM project WHERE id = :p AND default_facility_id IS NULL").param("p", project)
                .query(Integer.class).single()).isOne();
        assertThat(jdbc.sql("SELECT count(*) FROM project_item WHERE project_id = :p AND facility_id IS NULL").param("p", project)
                .query(Integer.class).single()).isOne();
    }

    @Test
    void aQuoteAndTheCostingOfAQuoteAreFrozenButTheDraftsCostingIsReplaced() {
        var project = project(newAccount());
        var quote = quote(project, 1);
        var ofQuote = costing(project, quote);
        var draft = costing(project, null);

        assertThatThrownBy(() -> jdbc.sql("UPDATE project_quote SET price = 1 WHERE id = :q").param("q", quote).update())
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("frozen");
        assertThatThrownBy(() -> jdbc.sql("UPDATE project_costing SET delivered_cost = 1 WHERE id = :c").param("c", ofQuote).update())
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("frozen");
        assertThat(jdbc.sql("UPDATE project_costing SET delivered_cost = 1 WHERE id = :c").param("c", draft).update()).isEqualTo(1);
    }

    @Test
    void deletingAQuoteTakesItsCostingAndTraceAndNothingElse() {
        var project = project(newAccount());
        var first = quote(project, 1);
        var second = quote(project, 2);
        var costingOfFirst = costing(project, first);
        trace(costingOfFirst);
        var costingOfSecond = costing(project, second);
        trace(costingOfSecond);
        var draft = costing(project, null);

        jdbc.sql("DELETE FROM project_quote WHERE id = :q").param("q", first).update();

        assertThat(count("project_costing", "id", costingOfFirst)).isZero();
        assertThat(count("project_trace", "costing_id", costingOfFirst)).isZero();
        assertThat(count("project_quote", "id", second)).isOne();
        assertThat(count("project_costing", "id", costingOfSecond)).isOne();
        assertThat(count("project_trace", "costing_id", costingOfSecond)).isOne();
        assertThat(count("project_costing", "id", draft)).isOne();
    }

    @Test
    void purgingATraceLeavesTheCostingAndTheQuote() {
        var project = project(newAccount());
        var quote = quote(project, 1);
        var costing = costing(project, quote);
        trace(costing);

        jdbc.sql("DELETE FROM project_trace WHERE costing_id = :c").param("c", costing).update();

        assertThat(count("project_costing", "id", costing)).isOne();
        assertThat(count("project_quote", "id", quote)).isOne();
    }

    @Test
    void aProjectHasOneDraftCostingAndAQuoteHasOneCosting() {
        var project = project(newAccount());
        var quote = quote(project, 1);
        costing(project, null);
        costing(project, quote);

        assertThatThrownBy(() -> costing(project, null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> costing(project, quote)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aCostingMustAgreeWithTheProjectOfItsQuote() {
        var account = newAccount();
        var quoteOfOne = quote(project(account), 1);
        var other = project(account);

        assertThatThrownBy(() -> costing(other, quoteOfOne)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void quoteNumbersAreOnePerProjectAndPositive() {
        var project = project(newAccount());
        quote(project, 1);

        assertThatThrownBy(() -> quote(project, 1)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> quote(project, 0)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aProjectHasOneLevelOfASkillFrom0To5() {
        var project = project(newAccount());
        jdbc.sql("INSERT INTO project_skill (project_id, skill_type_id, level) VALUES (:p, 1, 1)").param("p", project).update();

        for (var refused : new String[] {"(:p, 1, 2)", "(:p, 2, 6)", "(:p, 3, -1)"}) {
            assertThatThrownBy(() -> jdbc.sql("INSERT INTO project_skill (project_id, skill_type_id, level) VALUES " + refused)
                    .param("p", project).update()).as(refused).isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void theChecksRefuseValuesTheCostingCannotUse() {
        var account = newAccount();
        var project = project(account);

        assertRefused("UPDATE project SET margin_percent = 100 WHERE id = " + project);
        assertRefused("UPDATE project SET premium_percent = -100 WHERE id = " + project);
        assertRefused("UPDATE project SET status = 'open' WHERE id = " + project);
        assertRefused("UPDATE project SET industry_level = 6 WHERE id = " + project);
        assertRefused("UPDATE project SET max_job_seconds = 0 WHERE id = " + project);
        assertRefused("INSERT INTO project_item (project_id, type_id, me) VALUES (" + project + ", 1, 11)");
        assertRefused("INSERT INTO project_item (project_id, type_id, te) VALUES (" + project + ", 1, 21)");
        assertRefused("INSERT INTO project_product (project_id, type_id, quantity) VALUES (" + project + ", 1, 0)");
        assertRefused("INSERT INTO project_price (project_id, type_id, source, as_of) VALUES (" + project + ", 1, 'guess', now())");
        assertRefused("UPDATE facility SET tax_percent = 101 WHERE id = " + facility(account));
    }

    private void assertRefused(String sql) {
        assertThatThrownBy(() -> jdbc.sql(sql).update()).as(sql).isInstanceOf(DataIntegrityViolationException.class);
    }
}
