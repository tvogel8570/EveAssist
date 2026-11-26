package com.eveassist.industry.internal.costing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.eveassist.sde.Recipe;
import com.eveassist.sde.TypeId;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The numbers are worked out by hand. The recipe takes 900 s a run, and at TE 20 with no facility and no builder that is
 * 720 s, so the longest job is 2,592,000 / 720 = 3,600 runs.
 */
class BlueprintSplitterTest {

    private static final Recipe RECIPE = new Recipe(new TypeId(1), 1, 900, List.of());

    private static OwnedBlueprint original(int me, int te) {
        return new OwnedBlueprint(BlueprintKind.ORIGINAL, me, te, 0, BigDecimal.ZERO);
    }

    private static OwnedBlueprint copy(int me, int te, long runs) {
        return new OwnedBlueprint(BlueprintKind.COPY, me, te, runs, BigDecimal.ZERO);
    }

    private static List<ProjectJob> split(long runs, OwnedBlueprint... owned) {
        return BlueprintSplitter.split(RECIPE, runs, FacilityTerms.NONE, BuilderTerms.NONE, List.of(owned));
    }

    @Test
    void anOriginalMakesAllTheRunsInOneJob() {
        var jobs = split(100, original(10, 20));

        assertThat(jobs).singleElement().satisfies(job -> {
            assertThat(job.runs()).isEqualTo(100);
            assertThat(job.kind()).isEqualTo(BlueprintKind.ORIGINAL);
        });
    }

    @Test
    void theBestMeComesFirstAndTheNextBlueprintTakesTheRest() {
        var jobs = split(100, original(8, 20), copy(10, 20, 30));

        assertThat(jobs).extracting(ProjectJob::runs, job -> job.terms().me())
                .containsExactly(org.assertj.core.groups.Tuple.tuple(30L, 10), org.assertj.core.groups.Tuple.tuple(70L, 8));
    }

    @Test
    void theTimeEfficiencyBreaksATieInMeAndACopyComesBeforeAnOriginalAfterThat() {
        assertThat(split(10, copy(10, 10, 100), copy(10, 20, 100))).singleElement()
                .satisfies(job -> assertThat(job.terms().te()).isEqualTo(20));
        assertThat(split(10, original(10, 20), copy(10, 20, 100))).singleElement()
                .satisfies(job -> assertThat(job.kind()).isEqualTo(BlueprintKind.COPY));
    }

    @Test
    void aCopyGivesAtMostItsRunsAndAnEmptyCopyIsSkipped() {
        var jobs = split(100, copy(10, 20, 0), copy(9, 20, 40));

        assertThat(jobs).singleElement().satisfies(job -> assertThat(job.runs()).isEqualTo(40));
    }

    @Test
    void aLongJobIsCutIntoJobsOfAtMostTheMaximumRunsWithTheRemainderLast() {
        var jobs = split(8000, original(10, 20));

        assertThat(jobs).extracting(ProjectJob::runs).containsExactly(3600L, 3600L, 800L);
    }

    @Test
    void whenOneRunTakesLongerThanThirtyDaysEveryJobIsOneRun() {
        var slow = new Recipe(new TypeId(1), 1, 4_000_000, List.of());

        var jobs = BlueprintSplitter.split(slow, 5, FacilityTerms.NONE, BuilderTerms.NONE, List.of(original(10, 20)));

        assertThat(jobs).extracting(ProjectJob::runs).containsExactly(1L, 1L, 1L, 1L, 1L);
    }

    private static List<ProjectJob> split(long runs, SplitPolicy policy, OwnedBlueprint... owned) {
        return BlueprintSplitter.split(RECIPE, runs, FacilityTerms.NONE, BuilderTerms.NONE, List.of(owned), policy);
    }

    private static final SplitPolicy COPIES_FIRST = new SplitPolicy(SplitStrategy.COPIES_FIRST, null);

    @Test
    void copiesFirstUsesTheCopiesFromTheMostRunsToTheFewestAndThenAnOriginal() {
        var shortCopy = copy(10, 20, 20);
        var longCopy = copy(8, 20, 50);
        var original = original(10, 20);

        // 100 runs: the 50-run copy, the 20-run copy, then the original for the other 30
        assertThat(split(100, COPIES_FIRST, shortCopy, original, longCopy)).extracting(ProjectJob::runs, ProjectJob::kind)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(50L, BlueprintKind.COPY),
                        org.assertj.core.groups.Tuple.tuple(20L, BlueprintKind.COPY),
                        org.assertj.core.groups.Tuple.tuple(30L, BlueprintKind.ORIGINAL));
        // least cost takes the 20-run ME 10 copy and then the ME 10 original, and never touches the ME 8 copy
        assertThat(split(100, SplitPolicy.LEAST_COST, shortCopy, original, longCopy)).extracting(ProjectJob::runs)
                .containsExactly(20L, 80L);
    }

    @Test
    void copiesFirstTakesOnlyWhatIsNeededFromTheLargestCopy() {
        assertThat(split(40, COPIES_FIRST, copy(10, 20, 60), copy(10, 20, 30))).singleElement()
                .satisfies(job -> assertThat(job.runs()).isEqualTo(40));
    }

    @Test
    void copiesFirstLeavesTheRunsUncoveredWhenThereIsNoOriginal() {
        var jobs = split(100, COPIES_FIRST, copy(10, 20, 30), copy(10, 20, 20));

        assertThat(jobs).extracting(ProjectJob::runs).containsExactly(30L, 20L);
    }

    @Test
    void aTimeBoundGivesTheFewestJobsDividedEvenly() {
        // 720 s a run: a bound of 1,000,000 s allows 1,388 runs, and 8,000 runs need 6 jobs, not 5 (5 x 1,388 = 6,940)
        var jobs = split(8000, SplitPolicy.LEAST_COST.withMaxJobSeconds(1_000_000L), original(10, 20));

        assertThat(jobs).extracting(ProjectJob::runs).containsExactly(1334L, 1334L, 1333L, 1333L, 1333L, 1333L);
        assertThat(jobs).allSatisfy(job -> assertThat(JobCosting.seconds(900, job.runs(), job.terms())).isLessThanOrEqualTo(1_000_000));
    }

    @Test
    void aTimeBoundThatLimitsNothingChangesNothing() {
        var bound = SplitPolicy.LEAST_COST.withMaxJobSeconds(100L * 86400);

        assertThat(split(8000, bound, original(10, 20))).extracting(ProjectJob::runs).containsExactly(3600L, 3600L, 800L);
    }

    @Test
    void aTimeBoundAppliesToTheCopiesFirstOrderToo() {
        var bound = COPIES_FIRST.withMaxJobSeconds(7200L);

        // 720 s a run, so 10 runs a job: the copy's 25 runs in jobs of 9, 8 and 8, then the original's 5 in one
        assertThat(split(30, bound, copy(0, 20, 25), original(10, 20))).extracting(ProjectJob::runs, ProjectJob::kind)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(9L, BlueprintKind.COPY),
                        org.assertj.core.groups.Tuple.tuple(8L, BlueprintKind.COPY),
                        org.assertj.core.groups.Tuple.tuple(8L, BlueprintKind.COPY),
                        org.assertj.core.groups.Tuple.tuple(5L, BlueprintKind.ORIGINAL));
    }

    @Test
    void whenOneRunIsLongerThanTheTimeBoundEveryJobIsOneRun() {
        assertThat(split(3, SplitPolicy.LEAST_COST.withMaxJobSeconds(500L), original(10, 20)))
                .extracting(ProjectJob::runs).containsExactly(1L, 1L, 1L);
    }

    @Test
    void withNoBlueprintThereAreNoJobs() {
        assertThat(split(10)).isEmpty();
    }
}
