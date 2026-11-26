package com.eveassist.industry.internal.costing.domain;

import org.jmolecules.ddd.annotation.ValueObject;

/**
 * How an item's runs are shared among blueprints and cut into jobs (the developer's three scenarios of 2026-11-18). The
 * strategy orders the blueprints. The optional time bound is a limit on top of either: no job may take longer than that.
 *
 * @param maxJobSeconds the longest any job may take, or null for no bound beyond the 30-day limit every job has
 */
@ValueObject
public record SplitPolicy(SplitStrategy strategy, Long maxJobSeconds) {

    /** One job per blueprint, cut only at the 30-day limit, the best blueprint first. */
    public static final SplitPolicy LEAST_COST = new SplitPolicy(SplitStrategy.LEAST_COST, null);

    public SplitPolicy {
        if (strategy == null) {
            throw new IllegalArgumentException("A split policy needs a strategy");
        }
        if (maxJobSeconds != null && maxJobSeconds < 1) {
            throw new IllegalArgumentException("The longest job is at least one second, was " + maxJobSeconds);
        }
    }

    public SplitPolicy withMaxJobSeconds(Long seconds) {
        return new SplitPolicy(strategy, seconds);
    }
}
