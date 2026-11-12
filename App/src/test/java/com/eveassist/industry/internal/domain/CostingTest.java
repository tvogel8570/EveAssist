package com.eveassist.industry.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.eveassist.sde.Recipe;
import com.eveassist.sde.RecipeMaterial;
import com.eveassist.sde.TypeId;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CostingTest {

    private static final TypeId SHIP = new TypeId(1);
    private static final TypeId OTHER_SHIP = new TypeId(2);
    private static final TypeId PART = new TypeId(3);
    private static final TypeId ORE = new TypeId(4);
    private static final TypeId RARE_ORE = new TypeId(5);
    private static final TypeId AMMO = new TypeId(6);
    private static final TypeId CHEAP = new TypeId(7);

    private static final Map<TypeId, Recipe> RECIPES = Map.of(
            SHIP, recipe(SHIP, 1, new RecipeMaterial(PART, 2), new RecipeMaterial(ORE, 10)),
            OTHER_SHIP, recipe(OTHER_SHIP, 1, new RecipeMaterial(PART, 1), new RecipeMaterial(RARE_ORE, 1)),
            PART, recipe(PART, 1, new RecipeMaterial(ORE, 5)),
            AMMO, recipe(AMMO, 100, new RecipeMaterial(ORE, 3)),
            CHEAP, recipe(CHEAP, 1, new RecipeMaterial(ORE, 1)));

    private final Costing costing = new Costing(type -> Optional.ofNullable(RECIPES.get(type)), type -> {
        if (type.equals(ORE)) {
            return Optional.of(new BigDecimal("2.50"));
        }
        return type.equals(PART) ? Optional.of(new BigDecimal("100")) : Optional.empty();
    });

    private static Recipe recipe(TypeId product, int units, RecipeMaterial... materials) {
        return new Recipe(product, units, 600, List.of(materials));
    }

    private static LineCost line(PlanCost cost, TypeId type) {
        return cost.lines().stream().filter(line -> line.type().equals(type)).findFirst().orElseThrow();
    }

    @Test
    void walksTheTreeAndAddsUpTheBoughtItems() {
        var cost = costing.cost(List.of(new PlanProduct(SHIP, 2)));

        assertThat(line(cost, PART).quantity()).isEqualTo(4);
        assertThat(line(cost, PART).built()).isTrue();
        assertThat(line(cost, ORE).quantity()).isEqualTo(36);
        assertThat(cost.total()).isEqualByComparingTo("90");
    }

    @Test
    void sharedIntermediatesAreAddedUpAcrossProducts() {
        var cost = costing.cost(List.of(new PlanProduct(SHIP, 1), new PlanProduct(OTHER_SHIP, 1)));

        assertThat(line(cost, PART).quantity()).isEqualTo(3);
        assertThat(line(cost, ORE).quantity()).isEqualTo(23);
    }

    @Test
    void runsAreRoundedUpForBatches() {
        var cost = costing.cost(List.of(new PlanProduct(AMMO, 101)));

        assertThat(line(cost, ORE).quantity()).isEqualTo(6);
    }

    @Test
    void materialEfficiencyTakesTenPercentOffAndRoundsUpOncePerJob() {
        // 3 a run x 7 runs x 0.9 = 18.9, rounded up once to 19. Rounding each run (2.7 to 3) would give 21.
        var cost = costing.cost(List.of(new PlanProduct(AMMO, 700)));

        assertThat(line(cost, AMMO).runs()).isEqualTo(7);
        assertThat(line(cost, ORE).quantity()).isEqualTo(19);
    }

    @Test
    void aJobNeverUsesLessThanOneOfAMaterialForEachRun() {
        var cost = costing.cost(List.of(new PlanProduct(CHEAP, 10)));

        assertThat(line(cost, ORE).quantity()).isEqualTo(10);
    }

    @Test
    void aBuiltLineHasItsRunsUnitsMadeAndTimeAtTimeEfficiencyTwenty() {
        var cost = costing.cost(List.of(new PlanProduct(AMMO, 101)));

        var ammo = line(cost, AMMO);
        assertThat(ammo.runs()).isEqualTo(2);
        assertThat(ammo.unitsMade()).isEqualTo(200);
        assertThat(ammo.seconds()).isEqualTo(960);
    }

    @Test
    void runsOfASharedPartAreAddedUpBeforeTheyAreCounted() {
        var cost = costing.cost(List.of(new PlanProduct(SHIP, 1), new PlanProduct(OTHER_SHIP, 1)));

        assertThat(line(cost, PART).runs()).isEqualTo(3);
        assertThat(line(cost, SHIP).runs()).isEqualTo(1);
    }

    @Test
    void aBoughtLineHasNoRuns() {
        var cost = costing.cost(List.of(new PlanProduct(OTHER_SHIP, 1)), Set.of(PART));

        assertThat(line(cost, PART).runs()).isZero();
        assertThat(line(cost, PART).seconds()).isZero();
        assertThat(line(cost, RARE_ORE).runs()).isZero();
    }

    @Test
    void comparingNoticesAChangeInRunsEvenWhenTheUnitsAreTheSame() {
        var before = new PlanCost(List.of(new LineCost(PART, 5, true, true, null, null, 5, 5, 100)), BigDecimal.ZERO, 0);
        var after = new PlanCost(List.of(new LineCost(PART, 5, true, true, null, null, 6, 5, 100)), BigDecimal.ZERO, 0);

        assertThat(after.changedSince(before)).hasSize(1);
    }

    @Test
    void anItemWithNoPriceIsCountedAndLeftOutOfTheTotal() {
        var cost = costing.cost(List.of(new PlanProduct(OTHER_SHIP, 1)));

        assertThat(cost.unpricedLines()).isEqualTo(1);
        assertThat(line(cost, RARE_ORE).cost()).isNull();
        assertThat(cost.total()).isEqualByComparingTo("12.50");
    }

    @Test
    void aBoughtPartIsPricedAndNothingBelowItIsNeeded() {
        var cost = costing.cost(List.of(new PlanProduct(OTHER_SHIP, 1)), Set.of(PART));

        assertThat(line(cost, PART).built()).isFalse();
        assertThat(line(cost, PART).buildable()).isTrue();
        assertThat(line(cost, PART).cost()).isEqualByComparingTo("100");
        assertThat(cost.lines()).extracting(LineCost::type).doesNotContain(ORE);
    }

    @Test
    void aBoughtPartStillCountsDemandFromElsewhereOnce() {
        var cost = costing.cost(List.of(new PlanProduct(SHIP, 1)), Set.of(PART));

        assertThat(line(cost, ORE).quantity()).isEqualTo(9);
        assertThat(cost.total()).isEqualByComparingTo("222.50");
    }

    @Test
    void aProductIsAlwaysBuilt() {
        var cost = costing.cost(List.of(new PlanProduct(PART, 1)), Set.of(PART));

        assertThat(line(cost, PART).built()).isTrue();
    }

    @Test
    void comparingFindsLinesReachedThroughTheTree() {
        var before = costing.cost(List.of(new PlanProduct(SHIP, 1), new PlanProduct(OTHER_SHIP, 1)));
        var after = costing.cost(List.of(new PlanProduct(SHIP, 2), new PlanProduct(OTHER_SHIP, 1)));

        assertThat(after.changedSince(before)).extracting(LineCost::type)
                .containsExactlyInAnyOrder(SHIP, PART, ORE)
                .doesNotContain(OTHER_SHIP, RARE_ORE);
    }

    @Test
    void comparingFindsNewLines() {
        var before = costing.cost(List.of(new PlanProduct(AMMO, 1)));
        var after = costing.cost(List.of(new PlanProduct(AMMO, 1), new PlanProduct(OTHER_SHIP, 1)));

        assertThat(after.changedSince(before)).extracting(LineCost::type).contains(OTHER_SHIP, PART, RARE_ORE);
    }

    @Test
    void comparingIgnoresScaleOfAnAmount() {
        var one = new PlanCost(List.of(LineCost.bought(ORE, 1, false, new BigDecimal("2.5"), new BigDecimal("2.5"))),
                BigDecimal.ONE, 0);
        var other = new PlanCost(List.of(LineCost.bought(ORE, 1, false, new BigDecimal("2.50"), new BigDecimal("2.50"))),
                BigDecimal.ONE, 0);

        assertThat(other.changedSince(one)).isEmpty();
    }
}
