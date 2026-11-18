package com.eveassist.industry.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eveassist.industry.internal.domain.CostLine.Component;
import com.eveassist.industry.internal.domain.ProjectSettings.QuoteMode;
import com.eveassist.industry.internal.domain.ProjectWarning.Kind;
import com.eveassist.sde.Recipe;
import com.eveassist.sde.RecipeMaterial;
import com.eveassist.sde.TypeId;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

/**
 * Tests 1 and 8 use numbers read in the game on 2026-11-18 (see the requirements, Costing components). The others check
 * the project's own rules with numbers worked out by hand on a small recipe: PART is made 10 to a run from 7 ORE, SHIP_A
 * needs 10 PART and 100 ORE a run, and SHIP_B 5 PART and 50 ORE, all at the plan's ME 10 and TE 20.
 */
class ProjectCostingTest {

    private static final TypeId SHIP_A = new TypeId(1001);
    private static final TypeId SHIP_B = new TypeId(1002);
    private static final TypeId PART = new TypeId(1003);
    private static final TypeId ORE = new TypeId(1004);

    private final Map<TypeId, Recipe> recipes = new HashMap<>(Map.of(
            SHIP_A, new Recipe(SHIP_A, 1, 1000, List.of(new RecipeMaterial(PART, 10), new RecipeMaterial(ORE, 100))),
            SHIP_B, new Recipe(SHIP_B, 1, 1000, List.of(new RecipeMaterial(PART, 5), new RecipeMaterial(ORE, 50))),
            PART, new Recipe(PART, 10, 600, List.of(new RecipeMaterial(ORE, 7)))));
    private final Map<TypeId, BigDecimal> prices = new HashMap<>(Map.of(ORE, bd("3"), PART, bd("40")));
    private final Map<TypeId, BigDecimal> adjusted = new HashMap<>();
    private final Map<TypeId, BigDecimal> volumes = new HashMap<>();

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private ProjectCosting costing() {
        return new ProjectCosting(type -> Optional.ofNullable(recipes.get(type)),
                type -> Optional.ofNullable(prices.get(type)), type -> Optional.ofNullable(adjusted.get(type)),
                type -> Optional.ofNullable(volumes.get(type)));
    }

    private static ProjectSettings settings(
            String perHour, String perM3, String fee, QuoteMode mode, String margin, String premium, String step) {
        return new ProjectSettings(bd(perHour), bd(perM3), bd(fee), mode, bd(margin), bd(premium), bd(step));
    }

    private static ProjectSettings noCharges() {
        return settings("0", "0", "0", QuoteMode.MARGIN, "20", "0", "0");
    }

    private static ProjectDefinition project(
            List<PlanProduct> products, Set<TypeId> bought, Map<TypeId, ProjectBuild> builds, ProjectSettings settings) {
        return new ProjectDefinition(products, bought, builds, settings);
    }

    private static Map<TypeId, ProjectBuild> planBuilds() {
        var build = ProjectBuild.oneJob(JobTerms.PLAN);
        return Map.of(SHIP_A, build, SHIP_B, build, PART, build);
    }

    private static ProjectJob typedJob(long runs) {
        return new ProjectJob(runs, JobTerms.PLAN, BigDecimal.ZERO, BlueprintKind.TYPED);
    }

    private static void assertWithinOneIsk(BigDecimal actual, String expected) {
        assertThat(actual).isCloseTo(new BigDecimal(expected), Offset.offset(BigDecimal.ONE));
    }

    /**
     * Read in the game: 100 runs of a Cap Booster 800 copy at ME 0 and TE 0 in an Azbel in a wormhole, Industry and
     * Advanced Industry at V. The install cost was 198,879 ISK and the job made 1,000 charges. Every material is priced at
     * 1 ISK so the material line is the sum of the window's quantities, 219,681.
     */
    @Test
    void aProjectOfOneJobIsTheJobPlusTheLines() {
        var capBooster = new TypeId(11289);
        recipes.put(capBooster, new Recipe(capBooster, 10, 900, List.of(
                new RecipeMaterial(new TypeId(34), 1502), new RecipeMaterial(new TypeId(35), 440),
                new RecipeMaterial(new TypeId(36), 252), new RecipeMaterial(new TypeId(38), 13),
                new RecipeMaterial(new TypeId(39), 6), new RecipeMaterial(new TypeId(40), 6))));
        prices.clear();
        List.of(34, 35, 36, 38, 39, 40).forEach(id -> prices.put(new TypeId(id), BigDecimal.ONE));
        var adjustedPrices = Map.of(
                34, "2.9139375060564285", 35, "13.218374620307985", 36, "40.8409326615202", 38, "627.279129884147",
                39, "1078.5761333054306", 40, "1439.4304372972902");
        adjustedPrices.forEach((id, price) -> adjusted.put(new TypeId(id), bd(price)));
        var azbel = new FacilityTerms(bd("1"), bd("0"), bd("20"), bd("0"), bd("4"), bd("0.56883"), bd("0"), bd("4"));
        var terms = new JobTerms(0, 0, azbel, new BuilderTerms(5, 5, BigDecimal.ZERO));
        var settings = settings("0", "0", "10000", QuoteMode.MARGIN, "20", "0", "0");

        var cost = costing().cost(project(List.of(new PlanProduct(capBooster, 1000)), Set.of(),
                Map.of(capBooster, ProjectBuild.oneJob(terms)), settings));

        assertThat(cost.jobs()).hasSize(1);
        assertThat(cost.jobs().getFirst().job().seconds()).isEqualTo(13 * 3600 + 36 * 60);
        assertThat(cost.amount(Component.MATERIALS)).isEqualByComparingTo("219681");
        assertWithinOneIsk(cost.amount(Component.INSTALL_COST), "198879");
        assertThat(cost.amount(Component.SURPLUS_CREDIT)).isEqualByComparingTo("0");
        assertWithinOneIsk(cost.deliveredCost(), "428560");
        assertThat(cost.totalSeconds()).isEqualTo(48960);
        assertThat(cost.quotable()).isTrue();
    }

    @Test
    void aSharedPartIsBuiltOnceFromTheDemandOfEveryJobAboveIt() {
        var cost = costing().cost(project(
                List.of(new PlanProduct(SHIP_A, 2), new PlanProduct(SHIP_B, 3)), Set.of(), planBuilds(), noCharges()));

        // A, 2 runs: PART ceil(10 x 2 x 0.9) = 18, ORE 180. B, 3 runs: PART ceil(5 x 3 x 0.9 = 13.5) = 14, ORE 135.
        // PART demand 32 needs 4 runs (40 made); its ORE is max(4, ceil(7 x 4 x 0.9 = 25.2)) = 26.
        var part = cost.jobs().stream().map(ProjectJobCost::job).filter(job -> job.product().equals(PART)).findFirst().orElseThrow();
        assertThat(part.runs()).isEqualTo(4);
        assertThat(part.unitsMade()).isEqualTo(40);
        assertThat(cost.jobs()).hasSize(3);
        // A: 1,000 s x 2 runs x 0.8 = 1,600 s. B: 1,000 x 3 x 0.8 = 2,400 s. PART: 600 x 4 x 0.8 = 1,920 s.
        assertThat(cost.totalSeconds()).isEqualTo(5920);
        assertThat(cost.longestJobSeconds()).isEqualTo(2400);
        assertThat(cost.bought()).singleElement().satisfies(ore -> {
            assertThat(ore.type()).isEqualTo(ORE);
            assertThat(ore.quantity()).isEqualTo(341);
        });
        assertThat(cost.amount(Component.MATERIALS)).isEqualByComparingTo("1023");
        // 8 PART are left over and credited at 40 ISK
        assertThat(cost.amount(Component.SURPLUS_CREDIT)).isEqualByComparingTo("-320");
    }

    @Test
    void splittingAnItemIntoSmallerJobsCanUseMoreMaterial() {
        var oneJob = costing().cost(project(List.of(new PlanProduct(PART, 40)), Set.of(), planBuilds(), noCharges()));
        var split = new ProjectBuild.FixedJobs(java.util.Collections.nCopies(4, typedJob(1)));
        var fourJobs = costing().cost(project(List.of(new PlanProduct(PART, 40)), Set.of(), Map.of(PART, split), noCharges()));

        // 4 runs in one job: ceil(7 x 4 x 0.9 = 25.2) = 26. Four 1-run jobs: ceil(6.3) = 7 each, 28.
        assertThat(oneJob.bought().getFirst().quantity()).isEqualTo(26);
        assertThat(fourJobs.bought().getFirst().quantity()).isEqualTo(28);
        assertThat(fourJobs.jobs()).hasSize(4);
    }

    @Test
    void aBoughtPartIsPricedButHasNoJobAndNoEstimatedItemValue() {
        var cost = costing().cost(project(List.of(new PlanProduct(SHIP_A, 2), new PlanProduct(SHIP_B, 3)),
                Set.of(PART), planBuilds(), noCharges()));

        assertThat(cost.jobs()).extracting(job -> job.job().product()).containsExactlyInAnyOrder(SHIP_A, SHIP_B);
        // PART 18 + 14 = 32 at 40 ISK, ORE 180 + 135 = 315 at 3 ISK
        assertThat(cost.bought()).extracting(BoughtMaterial::type, BoughtMaterial::quantity)
                .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple(PART, 32L),
                        org.assertj.core.groups.Tuple.tuple(ORE, 315L));
        assertThat(cost.amount(Component.MATERIALS)).isEqualByComparingTo("2225");
    }

    @Test
    void surplusFromTheBatchSizeIsCreditedAndNotHauled() {
        volumes.put(PART, bd("2"));
        volumes.put(ORE, bd("1"));
        var settings = settings("0", "5", "0", QuoteMode.MARGIN, "20", "0", "0");

        var cost = costing().cost(project(List.of(new PlanProduct(PART, 95)), Set.of(), planBuilds(), settings));

        // 95 units need 10 runs, which make 100: 5 left over at 40 ISK
        assertThat(cost.jobs().getFirst().job().unitsMade()).isEqualTo(100);
        assertThat(cost.amount(Component.SURPLUS_CREDIT)).isEqualByComparingTo("-200");
        // ORE: max(10, ceil(7 x 10 x 0.9 = 63)) = 63 m3 in, and 95 PART x 2 = 190 m3 out, at 5 ISK. The 5 spare are not carried.
        assertThat(cost.amount(Component.HAULING)).isEqualByComparingTo("1265");
    }

    @Test
    void everyRateAndEveryLineAddsUpToTheDeliveredCost() {
        var single = new Recipe(SHIP_A, 1, 3600, List.of(new RecipeMaterial(ORE, 100)));
        recipes.clear();
        recipes.put(SHIP_A, single);
        prices.put(SHIP_A, bd("20000"));
        volumes.put(ORE, bd("2"));
        volumes.put(SHIP_A, bd("10"));
        var build = new ProjectBuild.Typed(JobTerms.PLAN, bd("50"));
        var settings = settings("1000", "5", "10000", QuoteMode.MARGIN, "20", "-5", "1000");

        var cost = costing().cost(project(List.of(new PlanProduct(SHIP_A, 2)), Set.of(), Map.of(SHIP_A, build), settings));

        // 2 runs at ME 10 and TE 20: ORE 180 at 3 = 540, 5,760 s = 1.6 h at 1,000 = 1,600, blueprint 2 x 50 = 100,
        // hauling (180 x 2 + 2 x 10) = 380 m3 at 5 = 1,900, and the fee 10,000. No install cost without a facility.
        assertThat(cost.lines()).extracting(CostLine::component, line -> line.amount().stripTrailingZeros())
                .contains(org.assertj.core.groups.Tuple.tuple(Component.MATERIALS, bd("540").stripTrailingZeros()),
                        org.assertj.core.groups.Tuple.tuple(Component.BLUEPRINT, bd("100").stripTrailingZeros()),
                        org.assertj.core.groups.Tuple.tuple(Component.TIME_VALUE, bd("1600").stripTrailingZeros()),
                        org.assertj.core.groups.Tuple.tuple(Component.HAULING, bd("1900").stripTrailingZeros()));
        assertThat(cost.deliveredCost()).isEqualByComparingTo("14140");
        assertThat(cost.costPerUnit()).isEqualByComparingTo("7070");
        // margin: 14,140 / 0.8 = 17,675, rounded up to 18,000. Market: 2 x 20,000 x 0.95 = 38,000.
        assertThat(cost.quote().price()).isEqualByComparingTo("18000");
        assertThat(cost.comparison().price()).isEqualByComparingTo("38000");
    }

    @Test
    void aMarginQuoteGivesExactlyThatShareOfThePriceAndAHundredPercentIsRefused() {
        var exact = costing().cost(project(List.of(new PlanProduct(PART, 10)), Set.of(), planBuilds(),
                settings("0", "0", "10000", QuoteMode.MARGIN, "12", "0", "0")));

        assertThat(exact.quote().realizedMarginPercent()).isCloseTo(bd("12"), Offset.offset(bd("0.0001")));
        assertThat(exact.quote().profit()).isEqualByComparingTo(
                exact.quote().price().multiply(bd("0.12")).setScale(2, java.math.RoundingMode.HALF_UP));

        var rounded = costing().cost(project(List.of(new PlanProduct(PART, 10)), Set.of(), planBuilds(),
                settings("0", "0", "10000", QuoteMode.MARGIN, "12", "0", "1000")));
        assertThat(rounded.quote().price().remainder(bd("1000"))).isEqualByComparingTo("0");
        assertThat(rounded.quote().realizedMarginPercent()).isGreaterThanOrEqualTo(bd("12"));

        assertThatThrownBy(() -> settings("0", "0", "0", QuoteMode.MARGIN, "100", "0", "0"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anItemWithoutAPriceIsListedAndBlocksTheQuote() {
        prices.remove(ORE);

        var cost = costing().cost(project(List.of(new PlanProduct(PART, 10)), Set.of(), planBuilds(), noCharges()));

        assertThat(cost.warnings()).contains(new ProjectWarning(Kind.UNPRICED_MATERIAL, ORE),
                new ProjectWarning(Kind.MISSING_ADJUSTED_PRICE, PART));
        assertThat(cost.bought().getFirst().cost()).isNull();
        assertThat(cost.quotable()).isFalse();
    }

    @Test
    void tooFewRunsAreAWarningAndTheProjectStillCosts() {
        var build = new ProjectBuild.FixedJobs(List.of(typedJob(3)));

        var cost = costing().cost(project(List.of(new PlanProduct(PART, 40)), Set.of(), Map.of(PART, build), noCharges()));

        assertThat(cost.warnings()).contains(new ProjectWarning(Kind.TOO_FEW_RUNS, PART));
        assertThat(cost.quotable()).isFalse();
    }

    @Test
    void ownedBlueprintsShareTheRunsAndAnOriginalIsOnlyANotice() {
        adjusted.put(ORE, bd("2"));
        var copy = new OwnedBlueprint(BlueprintKind.COPY, 10, 20, 3, bd("100"));
        var original = new OwnedBlueprint(BlueprintKind.ORIGINAL, 0, 0, 0, bd("5"));
        var build = new ProjectBuild.FromBlueprints(FacilityTerms.NONE, BuilderTerms.NONE, List.of(original, copy));

        var cost = costing().cost(project(List.of(new PlanProduct(PART, 40)), Set.of(), Map.of(PART, build), noCharges()));

        // 4 runs: the ME 10 copy takes 3 (ORE ceil(7 x 3 x 0.9 = 18.9) = 19), the ME 0 original the last one (7)
        assertThat(cost.jobs()).extracting(job -> job.job().runs()).containsExactly(3L, 1L);
        assertThat(cost.bought().getFirst().quantity()).isEqualTo(26);
        // the copy's 3 runs at 100 and the original's run at 5
        assertThat(cost.amount(Component.BLUEPRINT)).isEqualByComparingTo("305");
        assertThat(cost.warnings()).containsExactly(new ProjectWarning(Kind.USES_ORIGINAL, PART));
        assertThat(cost.quotable()).isTrue();
    }

    @Test
    void ownedCopiesThatAreTooShortLeaveTheProjectWithAWarning() {
        var copy = new OwnedBlueprint(BlueprintKind.COPY, 10, 20, 3, BigDecimal.ZERO);
        var build = new ProjectBuild.FromBlueprints(FacilityTerms.NONE, BuilderTerms.NONE, List.of(copy));

        var cost = costing().cost(project(List.of(new PlanProduct(PART, 40)), Set.of(), Map.of(PART, build), noCharges()));

        assertThat(cost.warnings()).contains(new ProjectWarning(Kind.TOO_FEW_RUNS, PART));
        assertThat(cost.quotable()).isFalse();
    }

    private static OwnedBlueprint original(int me, int te, String costPerRun) {
        return new OwnedBlueprint(BlueprintKind.ORIGINAL, me, te, 0, bd(costPerRun));
    }

    private static OwnedBlueprint copy(int me, int te, long runs, String costPerRun) {
        return new OwnedBlueprint(BlueprintKind.COPY, me, te, runs, bd(costPerRun));
    }

    @Test
    void aTimeBoundShowsWhatSplittingCostsAndWhatItBuys() {
        adjusted.put(ORE, bd("2"));
        var bound = SplitPolicy.LEAST_COST.withMaxJobSeconds(500L);
        var build = new ProjectBuild.FromBlueprints(FacilityTerms.NONE, BuilderTerms.NONE, List.of(original(10, 20, "0")), bound);

        var comparison = costing().compareToLeastCost(
                project(List.of(new PlanProduct(PART, 40)), Set.of(), Map.of(PART, build), noCharges()));

        // PART 40 needs 4 runs of 480 s at TE 20. One job: ORE ceil(7 x 4 x 0.9 = 25.2) = 26 at 3 ISK = 78.
        // Under a 500 s bound each run is its own job: ceil(6.3) = 7 each, 28 at 3 ISK = 84, so 6 ISK more.
        assertThat(comparison.leastCost().jobs()).hasSize(1);
        assertThat(comparison.chosen().jobs()).hasSize(4);
        assertThat(comparison.leastCost().amount(Component.MATERIALS)).isEqualByComparingTo("78");
        assertThat(comparison.chosen().amount(Component.MATERIALS)).isEqualByComparingTo("84");
        assertThat(comparison.deliveredCostDelta()).isEqualByComparingTo("6");
        assertThat(comparison.lineDeltas()).filteredOn(line -> line.component() == Component.MATERIALS)
                .singleElement().satisfies(line -> assertThat(line.amount()).isEqualByComparingTo("6"));
        // the longest job falls from 1,920 s to 480 s and the total stays 1,920 s. 6 ISK for 0.4 h is 15 ISK an hour.
        assertThat(comparison.longestJobSecondsSaved()).isEqualTo(1440);
        assertThat(comparison.totalSecondsDelta()).isZero();
        assertThat(comparison.costPerHourSaved()).isEqualByComparingTo("15");
    }

    @Test
    void usingCopiesFirstShowsTheCostOfTheDifferentBlueprints() {
        adjusted.put(ORE, bd("2"));
        var copiesFirst = new SplitPolicy(SplitStrategy.COPIES_FIRST, null);
        var owned = List.of(copy(0, 0, 3, "100"), original(10, 20, "0"));
        var build = new ProjectBuild.FromBlueprints(FacilityTerms.NONE, BuilderTerms.NONE, owned, copiesFirst);

        var comparison = costing().compareToLeastCost(
                project(List.of(new PlanProduct(PART, 40)), Set.of(), Map.of(PART, build), noCharges()));

        // Least cost: the ME 10 original makes all 4 runs, ORE 26 (78 ISK), 1,920 s in one job, no blueprint cost.
        // Copies first: the ME 0 / TE 0 copy's 3 runs (ORE 21, 1,800 s, 300 ISK for its runs), then 1 original run (ORE 7, 480 s).
        assertThat(comparison.leastCost().quotable()).isTrue();
        assertThat(comparison.chosen().amount(Component.MATERIALS)).isEqualByComparingTo("84");
        assertThat(comparison.chosen().amount(Component.BLUEPRINT)).isEqualByComparingTo("300");
        assertThat(comparison.deliveredCostDelta()).isEqualByComparingTo("306");
        // 1,800 + 480 s in all against 1,920 s, and the longest job is 1,800 s against 1,920 s: 120 s faster for 306 ISK
        assertThat(comparison.totalSecondsDelta()).isEqualTo(360);
        assertThat(comparison.longestJobSecondsSaved()).isEqualTo(120);
        assertThat(comparison.costPerHourSaved()).isEqualByComparingTo("9180");
    }

    @Test
    void whenTheTimeBoundCannotBeMetTheProjectSaysSoAndStillCosts() {
        var bound = SplitPolicy.LEAST_COST.withMaxJobSeconds(100L);
        var build = new ProjectBuild.FromBlueprints(FacilityTerms.NONE, BuilderTerms.NONE, List.of(original(10, 20, "0")), bound);
        adjusted.put(ORE, bd("2"));

        var cost = costing().cost(project(List.of(new PlanProduct(PART, 10)), Set.of(), Map.of(PART, build), noCharges()));

        // one run of 480 s is over a 100 s bound
        assertThat(cost.warnings()).contains(new ProjectWarning(Kind.TIME_BOUND_NOT_MET, PART));
        assertThat(cost.quotable()).isTrue();
    }

    @Test
    void aBuildThatDoesNotSplitIsTheSameAtLeastCost() {
        var comparison = costing().compareToLeastCost(
                project(List.of(new PlanProduct(PART, 40)), Set.of(), planBuilds(), noCharges()));

        assertThat(comparison.deliveredCostDelta()).isEqualByComparingTo("0");
        assertThat(comparison.costPerHourSaved()).isNull();
    }

    @Test
    void aTypedBlueprintIsCutAtTheThirtyDayLimitAndAtATimeBoundLikeAnyOther() {
        // PART makes 10 a run in 600 s, 480 s a run at TE 20. 3,600 s is 7 runs a job, so 4 runs are one job and 40 units of
        // 10 runs are 2 jobs. And 30 days is 5,400 runs of 480 s: 6,000 runs are cut into 5,400 and 600.
        var typed = new ProjectBuild.Typed(JobTerms.PLAN, BigDecimal.ZERO, SplitPolicy.LEAST_COST.withMaxJobSeconds(3_600L));

        assertThat(typed.jobsFor(recipes.get(PART), 10)).extracting(ProjectJob::runs).containsExactly(5L, 5L);
        assertThat(typed.jobsFor(recipes.get(PART), 4)).extracting(ProjectJob::runs).containsExactly(4L);
        assertThat(typed.jobsFor(recipes.get(PART), 4)).extracting(ProjectJob::kind).containsOnly(BlueprintKind.TYPED);
        assertThat(ProjectBuild.oneJob(JobTerms.PLAN).jobsFor(recipes.get(PART), 6000)).extracting(ProjectJob::runs)
                .containsExactly(5400L, 600L);
        assertThat(typed.timeBound()).hasValue(3_600L);
    }

    @Test
    void aMissingBlueprintChoiceIsRefusedBySayingWhichItem() {
        assertThatThrownBy(() -> costing().cost(project(List.of(new PlanProduct(PART, 10)), Set.of(), Map.of(), noCharges())))
                .hasMessageContaining("1003");
    }

    /**
     * Read in the game: 1 run of a 10000MN Afterburner I copy at ME 8 and TE 14 in the high-sec Azbel, Industry V, Advanced
     * Industry V, a BX-804. The window showed a job cost of 1,653,860 ISK and 04:29:29. Every material is priced at 1 ISK, so
     * the material line is the sum of the window's quantities, 1,748,029.
     */
    @Test
    void aBlueprintWithItsOwnMeAndTeWorksThroughTheWholeProject() {
        var afterburner = new TypeId(41236);
        recipes.put(afterburner, new Recipe(afterburner, 1, 36000, List.of(
                new RecipeMaterial(new TypeId(34), 1313168), new RecipeMaterial(new TypeId(35), 430794),
                new RecipeMaterial(new TypeId(36), 135255), new RecipeMaterial(new TypeId(37), 31628),
                new RecipeMaterial(new TypeId(38), 5480), new RecipeMaterial(new TypeId(39), 1748),
                new RecipeMaterial(new TypeId(40), 1146))));
        prices.clear();
        List.of(34, 35, 36, 37, 38, 39, 40).forEach(id -> prices.put(new TypeId(id), BigDecimal.ONE));
        Map.of(34, "2.9139375060564285", 35, "13.218374620307985", 36, "40.8409326615202", 37, "186.09791763273623",
                38, "627.279129884147", 39, "1078.5761333054306", 40, "1439.4304372972902")
                .forEach((id, price) -> adjusted.put(new TypeId(id), bd(price)));
        var azbel = new FacilityTerms(bd("1"), bd("0"), bd("20"), bd("0"), bd("4"), bd("2.00744"), bd("0"), bd("4"));
        var terms = new JobTerms(8, 14, azbel, new BuilderTerms(5, 5, bd("4")));
        var settings = settings("0", "0", "10000", QuoteMode.MARGIN, "20", "0", "0");

        var cost = costing().cost(project(List.of(new PlanProduct(afterburner, 1)), Set.of(),
                Map.of(afterburner, ProjectBuild.oneJob(terms)), settings));

        assertThat(cost.totalSeconds()).isEqualTo(4 * 3600 + 29 * 60 + 29);
        assertThat(cost.amount(Component.MATERIALS)).isEqualByComparingTo("1748029");
        assertWithinOneIsk(cost.amount(Component.INSTALL_COST), "1653860");
        assertWithinOneIsk(cost.deliveredCost(), "3411889");
    }
}
