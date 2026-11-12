package com.eveassist.industry.internal.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eveassist.IntegrationTest;
import com.eveassist.identity.Viewer;
import com.eveassist.industry.PlanId;
import com.eveassist.industry.internal.application.PlanRepository;
import com.eveassist.industry.internal.application.PlanTotal;
import com.eveassist.industry.internal.domain.Plan;
import com.eveassist.industry.internal.domain.PlanPrice;
import com.eveassist.industry.internal.domain.PlanProduct;
import com.eveassist.sde.TypeId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

class JdbcPlanRepositoryIT extends IntegrationTest {

    private static final Instant AS_OF = Instant.parse("2026-11-08T21:00:00Z");
    private static final PlanTotal TOTAL = new PlanTotal(new BigDecimal("12.50"), "test-sde", Instant.now());

    @Autowired
    PlanRepository plans;

    @Autowired
    JdbcClient jdbc;

    @Test
    void savesAndLoadsAPlanWithItsProducts() {
        var owner = viewerFor(newSub());
        var id = plans.create(owner, "Mine");
        var plan = plans.find(owner, id).orElseThrow();
        plan.addProduct(HAULER, 3);

        assertThat(plans.save(owner, plan, TOTAL)).contains(plan.version() + 1);

        var loaded = plans.find(owner, id).orElseThrow();
        assertThat(loaded.name()).isEqualTo("Mine");
        assertThat(loaded.products()).containsExactly(new PlanProduct(HAULER, 3));
    }

    @Test
    void savingRemovesProductsNoLongerInThePlan() {
        var owner = viewerFor(newSub());
        var id = plans.create(owner, "Mine");
        var plan = plans.find(owner, id).orElseThrow();
        plan.addProduct(HAULER, 3);
        plan.addProduct(SECOND_SHIP, 1);
        plans.save(owner, plan, TOTAL);

        var smaller = new Plan(id, "Mine", 1, List.of(new PlanProduct(SECOND_SHIP, 2)), Set.of(), Map.of());
        plans.save(owner, smaller, TOTAL);

        assertThat(plans.find(owner, id).orElseThrow().products()).containsExactly(new PlanProduct(SECOND_SHIP, 2));
    }

    @Test
    void savesAndClearsBoughtParts() {
        var owner = viewerFor(newSub());
        var id = plans.create(owner, "Mine");
        var plan = plans.find(owner, id).orElseThrow();
        plan.addProduct(HAULER, 1);
        plan.buyPart(COMPONENT);
        plans.save(owner, plan, TOTAL);

        var loaded = plans.find(owner, id).orElseThrow();
        assertThat(loaded.boughtParts()).containsExactly(COMPONENT);

        loaded.buildPart(COMPONENT);
        plans.save(owner, loaded, TOTAL);
        assertThat(plans.find(owner, id).orElseThrow().boughtParts()).isEmpty();
    }

    @Test
    void savingAnOutOfDatePlanChangesNothing() {
        var owner = viewerFor(newSub());
        var id = plans.create(owner, "Mine");
        var first = plans.find(owner, id).orElseThrow();
        var second = plans.find(owner, id).orElseThrow();
        first.addProduct(HAULER, 1);
        second.addProduct(SECOND_SHIP, 1);

        assertThat(plans.save(owner, first, TOTAL)).isPresent();
        assertThat(plans.save(owner, second, TOTAL)).isEmpty();
        assertThat(plans.find(owner, id).orElseThrow().products()).containsExactly(new PlanProduct(HAULER, 1));
    }

    @Test
    void anotherAccountCannotFindSaveOrListAPlan() {
        var owner = viewerFor(newSub());
        var other = viewerFor(newSub());
        var id = plans.create(owner, "Mine");
        var plan = plans.find(owner, id).orElseThrow();
        plan.addProduct(HAULER, 3);

        assertThat(plans.find(other, id)).isEmpty();
        assertThat(plans.save(other, plan, TOTAL)).isEmpty();
        assertThat(plans.overviews(other, "test-sde")).isEmpty();
        assertThat(plans.find(owner, id).orElseThrow().products()).isEmpty();
    }

    @Test
    void anUnknownPlanLooksTheSameAsAnotherAccountsPlan() {
        assertThat(plans.find(viewerFor(newSub()), new PlanId(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void overviewShowsTheStoredTotalAndWhetherItIsOutOfDate() {
        var owner = viewerFor(newSub());
        var id = plans.create(owner, "Mine");
        var plan = plans.find(owner, id).orElseThrow();
        plan.addProduct(HAULER, 3);
        plans.save(owner, plan, TOTAL);

        var current = plans.overviews(owner, "test-sde").getFirst();
        assertThat(current.totalCost()).isEqualByComparingTo("12.50");
        assertThat(current.productCount()).isEqualTo(1);
        assertThat(current.outOfDate()).isFalse();
        assertThat(plans.overviews(owner, "newer-sde").getFirst().outOfDate()).isTrue();
    }

    @Test
    void aPlansPricesAreSavedAndLoadedWithIt() {
        var owner = viewerFor(newSub());
        var id = plans.create(owner, "Mine");
        var plan = plans.find(owner, id).orElseThrow();
        plan.addProduct(HAULER, 3);
        plan.setPrice(TRITANIUM, new PlanPrice(new BigDecimal("5.25"), new BigDecimal("4.75"), AS_OF));
        plan.setPrice(PILOTS_MINERAL, new PlanPrice(null, null, AS_OF));
        plans.save(owner, plan, TOTAL);

        var loaded = plans.find(owner, id).orElseThrow();

        assertThat(loaded.prices()).containsOnlyKeys(TRITANIUM, PILOTS_MINERAL);
        assertThat(loaded.prices().get(TRITANIUM).sell()).isEqualByComparingTo("5.25");
        assertThat(loaded.prices().get(TRITANIUM).buy()).isEqualByComparingTo("4.75");
        assertThat(loaded.prices().get(PILOTS_MINERAL).sell()).isNull();
        assertThat(loaded.prices().get(TRITANIUM).asOf()).isEqualTo(AS_OF);
    }

    @Test
    void aNewPriceReplacesTheOldOneAndADroppedPriceIsRemoved() {
        var owner = viewerFor(newSub());
        var plan = costedPlan(owner);
        plan.setPrice(TRITANIUM, new PlanPrice(new BigDecimal("5.00"), null, AS_OF));
        plan.setPrice(PILOTS_MINERAL, new PlanPrice(new BigDecimal("10.00"), null, AS_OF));
        var saved = plans.save(owner, plan, TOTAL).orElseThrow();

        var again = plans.find(owner, plan.id()).orElseThrow();
        again.setPrice(TRITANIUM, new PlanPrice(new BigDecimal("6.00"), null, AS_OF.plusSeconds(60)));
        again.keepPricesFor(Set.of(TRITANIUM));
        assertThat(again.version()).isEqualTo(saved);
        plans.save(owner, again, TOTAL);

        var loaded = plans.find(owner, plan.id()).orElseThrow();
        assertThat(loaded.prices()).containsOnlyKeys(TRITANIUM);
        assertThat(loaded.prices().get(TRITANIUM).sell()).isEqualByComparingTo("6.00");
    }

    @Test
    void anotherPlansPricesAreNotTouched() {
        var owner = viewerFor(newSub());
        var first = costedPlan(owner);
        first.setPrice(TRITANIUM, new PlanPrice(new BigDecimal("5.00"), null, AS_OF));
        plans.save(owner, first, TOTAL);
        var second = costedPlan(owner);
        second.setPrice(TRITANIUM, new PlanPrice(new BigDecimal("9.00"), null, AS_OF));
        plans.save(owner, second, TOTAL);

        assertThat(plans.find(owner, first.id()).orElseThrow().prices().get(TRITANIUM).sell())
                .isEqualByComparingTo("5.00");
    }

    @Test
    void aPriceNeverMarksAPlanOutOfDate() {
        var owner = viewerFor(newSub());
        var plan = costedPlan(owner);
        assertThat(plans.overviews(owner, "test-sde").getFirst().outOfDate()).isFalse();

        plan = plans.find(owner, plan.id()).orElseThrow();
        plan.setPrice(TRITANIUM, new PlanPrice(new BigDecimal("6.00"), null, Instant.now().plusSeconds(3600)));
        plans.save(owner, plan, TOTAL);

        assertThat(plans.overviews(owner, "test-sde").getFirst().outOfDate()).isFalse();
    }

    @Test
    void theDatabaseRefusesAPriceFromAnUnknownSource() {
        var plan = costedPlan(viewerFor(newSub()));
        var planKey = jdbc.sql("SELECT id FROM plan WHERE public_id = :id").param("id", plan.id().value())
                .query(Long.class).single();

        assertThatThrownBy(() -> jdbc.sql("INSERT INTO plan_price (plan_id, type_id, source, as_of) VALUES (:plan, 34, 'guess', now())")
                .param("plan", planKey).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theDatabaseRefusesAPriceForAPlanThatDoesNotExist() {
        assertThatThrownBy(() -> jdbc.sql("INSERT INTO plan_price (plan_id, type_id, as_of) VALUES (-1, 34, now())").update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingAPlanDeletesItsPrices() {
        var owner = viewerFor(newSub());
        var plan = costedPlan(owner);
        plan.setPrice(TRITANIUM, new PlanPrice(new BigDecimal("5.00"), null, AS_OF));
        plans.save(owner, plan, TOTAL);
        var planKey = jdbc.sql("SELECT id FROM plan WHERE public_id = :id").param("id", plan.id().value())
                .query(Long.class).single();

        jdbc.sql("DELETE FROM plan WHERE id = :id").param("id", planKey).update();

        assertThat(jdbc.sql("SELECT count(*) FROM plan_price WHERE plan_id = :id").param("id", planKey)
                .query(Long.class).single()).isZero();
    }

    @Test
    void recordingACostingClearsTheMarkAndKeepsTheVersion() {
        var owner = viewerFor(newSub());
        var plan = costedPlan(owner);

        var refreshed = new PlanTotal(new BigDecimal("99.00"), "newer-sde", Instant.now());
        plans.recordCosting(owner, plan, refreshed);

        var overview = plans.overviews(owner, "newer-sde").getFirst();
        assertThat(overview.totalCost()).isEqualByComparingTo("99.00");
        assertThat(plans.find(owner, plan.id()).orElseThrow().version()).isEqualTo(plan.version());
    }

    @Test
    void recordingAnOlderCostingCannotOverwriteANewerEdit() {
        var owner = viewerFor(newSub());
        var stale = costedPlan(owner);
        var current = plans.find(owner, stale.id()).orElseThrow();
        current.addProduct(SECOND_SHIP, 1);
        plans.save(owner, current, TOTAL);

        plans.recordCosting(owner, stale, new PlanTotal(new BigDecimal("1.00"), "older", Instant.now()));

        assertThat(plans.overviews(owner, "test-sde").getFirst().totalCost()).isEqualByComparingTo("12.50");
    }

    @Test
    void anotherAccountCannotRecordACosting() {
        var owner = viewerFor(newSub());
        var plan = costedPlan(owner);

        plans.recordCosting(viewerFor(newSub()), plan,
                new PlanTotal(new BigDecimal("1.00"), "other", Instant.now()));

        assertThat(plans.overviews(owner, "test-sde").getFirst().totalCost()).isEqualByComparingTo("12.50");
    }

    private Plan costedPlan(Viewer owner) {
        var id = plans.create(owner, "Mine");
        var plan = plans.find(owner, id).orElseThrow();
        plan.addProduct(HAULER, 3);
        plans.save(owner, plan, TOTAL);
        return plans.find(owner, id).orElseThrow();
    }

    @Test
    void aPlanNotYetCostedHasNoTotalAndIsNotOutOfDate() {
        var owner = viewerFor(newSub());
        plans.create(owner, "Empty");

        var overview = plans.overviews(owner, "test-sde").getFirst();
        assertThat(overview.totalCost()).isNull();
        assertThat(overview.outOfDate()).isFalse();
    }

    // ---- archiving and deleting

    @Test
    void anArchivedPlanIsLeftOutOfTheListUnlessAskedForAndComesAfterTheActiveOnes() {
        var owner = viewerFor(newSub());
        var a = plans.create(owner, "A first");
        plans.create(owner, "B second");

        assertThat(plans.setArchived(owner, a, true)).isTrue();

        assertThat(plans.overviews(owner, "test-sde")).extracting(o -> o.name()).containsExactly("B second");
        var all = plans.overviews(owner, "test-sde", true);
        assertThat(all).extracting(o -> o.name()).containsExactly("B second", "A first");
        assertThat(all).extracting(o -> o.archived()).containsExactly(false, true);
        assertThat(plans.find(owner, a).orElseThrow().archived()).isTrue();
    }

    @Test
    void archivingAndRestoringAreNotEditsAndRestoringBringsThePlanBack() {
        var owner = viewerFor(newSub());
        var id = plans.create(owner, "Mine");
        var plan = plans.find(owner, id).orElseThrow();
        plan.addProduct(HAULER, 3);
        plans.save(owner, plan, TOTAL);
        var version = plans.find(owner, id).orElseThrow().version();

        plans.setArchived(owner, id, true);
        plans.setArchived(owner, id, true);
        assertThat(plans.find(owner, id).orElseThrow().version()).isEqualTo(version);
        plans.setArchived(owner, id, false);

        assertThat(plans.find(owner, id).orElseThrow().archived()).isFalse();
        assertThat(plans.find(owner, id).orElseThrow().version()).isEqualTo(version);
        assertThat(plans.overviews(owner, "test-sde")).extracting(o -> o.name()).containsExactly("Mine");
    }

    @Test
    void anAnArchivedPlanCanStillBeEditedAndKeepsItsStatus() {
        var owner = viewerFor(newSub());
        var id = plans.create(owner, "Mine");
        plans.setArchived(owner, id, true);
        var plan = plans.find(owner, id).orElseThrow();
        plan.addProduct(HAULER, 2);

        assertThat(plans.save(owner, plan, TOTAL)).isPresent();

        assertThat(plans.find(owner, id).orElseThrow().archived()).isTrue();
    }

    @Test
    void deletingAPlanRemovesItWithItsProductsPartsAndPrices() {
        var owner = viewerFor(newSub());
        var id = plans.create(owner, "Mine");
        var plan = plans.find(owner, id).orElseThrow();
        plan.addProduct(HAULER, 3);
        plan.buyPart(SECOND_SHIP);
        plan.setPrice(HAULER, new PlanPrice(new BigDecimal("1.00"), new BigDecimal("0.50"), AS_OF));
        plans.save(owner, plan, TOTAL);

        assertThat(plans.delete(owner, id)).isTrue();

        assertThat(plans.find(owner, id)).isEmpty();
        assertThat(plans.overviews(owner, "test-sde", true)).isEmpty();
        for (var table : new String[] {"plan_product", "plan_bought_part", "plan_price"}) {
            assertThat(jdbc.sql("SELECT count(*) FROM " + table + " t WHERE NOT EXISTS (SELECT 1 FROM plan p WHERE p.id = t.plan_id)")
                    .query(Long.class).single()).as(table + " rows with no plan").isZero();
        }
        assertThat(plans.delete(owner, id)).as("deleting it again finds nothing").isFalse();
    }

    @Test
    void anotherAccountCannotArchiveRestoreOrDeleteAPlan() {
        var owner = viewerFor(newSub());
        var other = viewerFor(newSub());
        var id = plans.create(owner, "Mine");

        assertThat(plans.setArchived(other, id, true)).isFalse();
        assertThat(plans.delete(other, id)).isFalse();

        assertThat(plans.find(owner, id).orElseThrow().archived()).isFalse();
        assertThat(plans.overviews(owner, "test-sde")).hasSize(1);
    }
}
