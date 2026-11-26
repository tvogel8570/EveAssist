package com.eveassist.industry.internal.costing.domain;

import com.eveassist.industry.internal.domain.ProjectCosting;
import com.eveassist.sde.Recipe;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Shares the runs an item needs among the blueprints the developer owns, and cuts each blueprint's runs into jobs. The
 * {@link SplitPolicy} says which blueprint comes first and whether a job's time is bounded (decided on 2026-11-18):
 *
 * <ul>
 * <li>{@link SplitStrategy#LEAST_COST}: the best ME first, then the best TE, a copy before an original on a tie.</li>
 * <li>{@link SplitStrategy#COPIES_FIRST}: the copies from the most runs to the fewest (then the better ME), then the
 * originals, the best ME first.</li>
 * </ul>
 *
 * A copy gives at most the runs it has left, and an original gives as many as are still needed. A blueprint's runs are
 * cut into the fewest jobs that fit {@link JobCosting#maximumRuns} (30 days) and, when the policy has one, the time bound.
 * With only the 30-day limit the jobs are full and the remainder is last. Where a time bound is what limits the jobs, the
 * runs are divided evenly so the jobs finish together; a bound that limits nothing changes nothing. Every job rounds its materials up on its own, so more jobs can use a little more material.
 * If the blueprints cannot cover the runs the jobs make too few units and {@link ProjectCosting} reports it.
 */
public final class BlueprintSplitter {

    private BlueprintSplitter() {
    }

    public static List<ProjectJob> split(
            Recipe recipe, long runsNeeded, FacilityTerms facility, BuilderTerms builder, List<OwnedBlueprint> owned) {
        return split(recipe, runsNeeded, facility, builder, owned, SplitPolicy.LEAST_COST);
    }

    public static List<ProjectJob> split(
            Recipe recipe, long runsNeeded, FacilityTerms facility, BuilderTerms builder, List<OwnedBlueprint> owned,
            SplitPolicy policy) {
        List<ProjectJob> jobs = new ArrayList<>();
        long remaining = runsNeeded;
        for (OwnedBlueprint blueprint : owned.stream().sorted(order(policy.strategy())).toList()) {
            long runs = blueprint.kind() == BlueprintKind.ORIGINAL ? remaining : Math.min(remaining, blueprint.runsLeft());
            if (runs < 1) {
                continue;
            }
            var terms = new JobTerms(blueprint.me(), blueprint.te(), facility, builder);
            jobs.addAll(cut(recipe, runs, terms, blueprint.costPerRun(), blueprint.kind(), policy));
            remaining -= runs;
            if (remaining == 0) {
                break;
            }
        }
        return jobs;
    }

    /**
     * Cuts the runs of one blueprint into the fewest jobs that fit the 30-day limit and the policy's time bound, if it has one.
     * The same rule serves a typed blueprint, which is not owned and has no limit on its runs.
     */
    public static List<ProjectJob> cut(
            Recipe recipe, long runs, JobTerms terms, java.math.BigDecimal costPerRun, BlueprintKind kind, SplitPolicy policy) {
        long cap = JobCosting.maximumRuns(recipe.secondsPerRun(), terms);
        boolean boundLimits = false;
        if (policy.maxJobSeconds() != null) {
            long boundCap = JobCosting.maximumRunsWithin(recipe.secondsPerRun(), terms, policy.maxJobSeconds());
            boundLimits = boundCap < cap;
            cap = Math.min(cap, boundCap);
        }
        List<ProjectJob> jobs = new ArrayList<>();
        for (long size : jobSizes(runs, cap, boundLimits)) {
            jobs.add(new ProjectJob(size, terms, costPerRun, kind));
        }
        return jobs;
    }

    private static Comparator<OwnedBlueprint> order(SplitStrategy strategy) {
        Comparator<OwnedBlueprint> bestFirst = Comparator.comparingInt(OwnedBlueprint::me).reversed()
                .thenComparing(Comparator.comparingInt(OwnedBlueprint::te).reversed());
        return switch (strategy) {
            case LEAST_COST -> bestFirst.thenComparing(blueprint -> blueprint.kind() == BlueprintKind.COPY ? 0 : 1);
            case COPIES_FIRST -> Comparator
                    .<OwnedBlueprint>comparingInt(blueprint -> blueprint.kind() == BlueprintKind.COPY ? 0 : 1)
                    .thenComparing(Comparator.comparingLong(OwnedBlueprint::runsLeft).reversed())
                    .thenComparing(bestFirst);
        };
    }

    /** The fewest jobs of at most {@code cap} runs: full jobs and a remainder, or as equal as possible. */
    private static List<Long> jobSizes(long runs, long cap, boolean even) {
        long count = Math.ceilDiv(runs, cap);
        List<Long> sizes = new ArrayList<>();
        if (even) {
            for (long i = 0; i < count; i++) {
                sizes.add(runs / count + (i < runs % count ? 1 : 0));
            }
        } else {
            for (long left = runs; left > 0; left -= cap) {
                sizes.add(Math.min(left, cap));
            }
        }
        return sizes;
    }
}
