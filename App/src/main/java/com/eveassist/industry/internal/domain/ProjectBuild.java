package com.eveassist.industry.internal.domain;

import com.eveassist.industry.internal.costing.domain.BlueprintKind;
import com.eveassist.industry.internal.costing.domain.BlueprintSplitter;
import com.eveassist.industry.internal.costing.domain.BuilderTerms;
import com.eveassist.industry.internal.costing.domain.FacilityTerms;
import com.eveassist.industry.internal.costing.domain.JobTerms;
import com.eveassist.industry.internal.costing.domain.OwnedBlueprint;
import com.eveassist.industry.internal.costing.domain.ProjectJob;
import com.eveassist.industry.internal.costing.domain.SplitPolicy;
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

    /** A typed blueprint under the given terms that adds no cost, cut only at the 30-day limit. */
    static ProjectBuild oneJob(JobTerms terms) {
        return new Typed(terms, BigDecimal.ZERO);
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

    /**
     * A blueprint whose ME and TE are typed in, which is not owned (or is a what-if): it has no limit on its runs, and they go into the
     * fewest jobs that fit the 30-day limit and the policy's time bound.
     */
    record Typed(JobTerms terms, BigDecimal blueprintCostPerRun, SplitPolicy policy) implements ProjectBuild {

        public Typed {
            if (terms == null || blueprintCostPerRun == null || policy == null) {
                throw new IllegalArgumentException("A typed build needs terms, a blueprint cost and a split policy");
            }
        }

        /** With no time bound. */
        public Typed(JobTerms terms, BigDecimal blueprintCostPerRun) {
            this(terms, blueprintCostPerRun, SplitPolicy.LEAST_COST);
        }

        @Override
        public List<ProjectJob> jobsFor(Recipe recipe, long runsNeeded) {
            return BlueprintSplitter.cut(recipe, runsNeeded, terms, blueprintCostPerRun, BlueprintKind.TYPED, policy);
        }

        @Override
        public OptionalLong timeBound() {
            return policy.maxJobSeconds() == null ? OptionalLong.empty() : OptionalLong.of(policy.maxJobSeconds());
        }

        @Override
        public ProjectBuild withPolicy(SplitPolicy other) {
            return new Typed(terms, blueprintCostPerRun, other);
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
