package com.eveassist.industry.internal.domain;

import com.eveassist.sde.Recipe;
import java.math.BigDecimal;
import java.util.List;
import java.util.OptionalLong;

/**
 * How one built item is made in a project. The runs the item needs are only known once the jobs above it are costed, so a
 * build gives its jobs when asked for a number of runs. Materials are rounded per job, so the jobs' sizes change the cost.
 */
public sealed interface ProjectBuild {

    /** The jobs that make at least {@code runsNeeded} runs, or as many as the build can. */
    List<ProjectJob> jobsFor(Recipe recipe, long runsNeeded);

    /** The longest any job may take under this build's policy, if it has a bound. */
    default OptionalLong timeBound() {
        return OptionalLong.empty();
    }

    /** The same build under another split policy. Builds that do not split are unchanged. */
    default ProjectBuild withPolicy(SplitPolicy policy) {
        return this;
    }

    /** One job with all the runs needed under typed terms, and a blueprint that adds no cost. */
    static ProjectBuild oneJob(JobTerms terms) {
        return new OneJob(terms, BigDecimal.ZERO);
    }

    /** Jobs that are given, whatever the item needs. Runs beyond the need are surplus and too few are a warning. */
    record FixedJobs(List<ProjectJob> jobs) implements ProjectBuild {

        public FixedJobs {
            jobs = List.copyOf(jobs);
            if (jobs.isEmpty()) {
                throw new IllegalArgumentException("A build with fixed jobs needs at least one job");
            }
        }

        @Override
        public List<ProjectJob> jobsFor(Recipe recipe, long runsNeeded) {
            return jobs;
        }
    }

    /** One job with exactly the runs needed, under typed terms (a blueprint not owned, or a what-if). */
    record OneJob(JobTerms terms, BigDecimal blueprintCostPerRun) implements ProjectBuild {

        @Override
        public List<ProjectJob> jobsFor(Recipe recipe, long runsNeeded) {
            return List.of(new ProjectJob(runsNeeded, terms, blueprintCostPerRun, BlueprintKind.TYPED));
        }
    }

    /** The runs are shared among owned blueprints by {@link BlueprintSplitter}, under a {@link SplitPolicy}. */
    record FromBlueprints(FacilityTerms facility, BuilderTerms builder, List<OwnedBlueprint> owned, SplitPolicy policy)
            implements ProjectBuild {

        public FromBlueprints {
            owned = List.copyOf(owned);
            if (policy == null) {
                throw new IllegalArgumentException("A build needs a split policy");
            }
        }

        /** At least cost, with no time bound. */
        public FromBlueprints(FacilityTerms facility, BuilderTerms builder, List<OwnedBlueprint> owned) {
            this(facility, builder, owned, SplitPolicy.LEAST_COST);
        }

        @Override
        public List<ProjectJob> jobsFor(Recipe recipe, long runsNeeded) {
            return BlueprintSplitter.split(recipe, runsNeeded, facility, builder, owned, policy);
        }

        @Override
        public OptionalLong timeBound() {
            return policy.maxJobSeconds() == null ? OptionalLong.empty() : OptionalLong.of(policy.maxJobSeconds());
        }

        @Override
        public ProjectBuild withPolicy(SplitPolicy other) {
            return new FromBlueprints(facility, builder, owned, other);
        }
    }
}
