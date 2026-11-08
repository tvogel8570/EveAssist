package com.eveassist.industry.internal.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.eveassist.IntegrationTest;
import com.eveassist.identity.Viewer;
import com.eveassist.industry.PlanId;
import com.eveassist.industry.internal.application.PlanRepository;
import com.eveassist.industry.internal.application.PlanTotal;
import com.eveassist.industry.internal.domain.Plan;
import com.eveassist.industry.internal.domain.PlanProduct;
import com.eveassist.market.JitaPrice;
import com.eveassist.sde.TypeId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class JdbcPlanRepositoryIT extends IntegrationTest {

    private static final PlanTotal TOTAL = new PlanTotal(new BigDecimal("12.50"), "test-sde", Instant.now(), Set.of(TRITANIUM));

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

        var smaller = new Plan(id, "Mine", 1, List.of(new PlanProduct(SECOND_SHIP, 2)), Set.of());
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
    void aNewerPriceForAnItemThePlanUsesMarksItOutOfDate() {
        var owner = viewerFor(newSub());
        costedPlan(owner);
        assertThat(plans.overviews(owner, "test-sde").getFirst().outOfDate()).isFalse();

        storeNewerPrice(TRITANIUM);

        assertThat(plans.overviews(owner, "test-sde").getFirst().outOfDate()).isTrue();
    }

    @Test
    void aNewerPriceForAnotherItemDoesNot() {
        var owner = viewerFor(newSub());
        costedPlan(owner);

        storeNewerPrice(PILOTS_MINERAL);

        assertThat(plans.overviews(owner, "test-sde").getFirst().outOfDate()).isFalse();
    }

    @Test
    void recordingACostingClearsTheMarkAndKeepsTheVersion() {
        var owner = viewerFor(newSub());
        var plan = costedPlan(owner);
        storeNewerPrice(TRITANIUM);
        jdbc.sql("UPDATE plan SET costed_at = now() + interval '1 hour' WHERE public_id = :id")
                .param("id", plan.id().value()).update();

        var refreshed = new PlanTotal(new BigDecimal("99.00"), "newer-sde", Instant.now(), Set.of(TRITANIUM));
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

        plans.recordCosting(owner, stale, new PlanTotal(new BigDecimal("1.00"), "older", Instant.now(), Set.of()));

        assertThat(plans.overviews(owner, "test-sde").getFirst().totalCost()).isEqualByComparingTo("12.50");
    }

    @Test
    void anotherAccountCannotRecordACosting() {
        var owner = viewerFor(newSub());
        var plan = costedPlan(owner);

        plans.recordCosting(viewerFor(newSub()), plan,
                new PlanTotal(new BigDecimal("1.00"), "other", Instant.now(), Set.of()));

        assertThat(plans.overviews(owner, "test-sde").getFirst().totalCost()).isEqualByComparingTo("12.50");
    }

    private Plan costedPlan(Viewer owner) {
        var id = plans.create(owner, "Mine");
        var plan = plans.find(owner, id).orElseThrow();
        plan.addProduct(HAULER, 3);
        plans.save(owner, plan, TOTAL);
        return plans.find(owner, id).orElseThrow();
    }

    /** Later than the costing whatever the two clocks say, as prices are shared by all tests. */
    private void storeNewerPrice(TypeId type) {
        market.store(List.of(new JitaPrice(type, new BigDecimal("6.00"), Instant.now().plusSeconds(30))));
    }

    @Test
    void aPlanNotYetCostedHasNoTotalAndIsNotOutOfDate() {
        var owner = viewerFor(newSub());
        plans.create(owner, "Empty");

        var overview = plans.overviews(owner, "test-sde").getFirst();
        assertThat(overview.totalCost()).isNull();
        assertThat(overview.outOfDate()).isFalse();
    }
}
