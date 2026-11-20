package com.eveassist.industry.internal.domain;

import java.math.BigDecimal;
import java.util.List;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * A project costed with its split policy next to the same project at least cost (one job per blueprint, the best blueprint
 * first, cut only at 30 days), so the price of finishing sooner can be read.
 *
 * @param chosen the costing under the project's policy
 * @param leastCost the same project under {@link SplitPolicy#LEAST_COST}
 * @param lineDeltas for each cost line, the chosen amount less the least-cost amount. Positive is more expensive.
 * @param deliveredCostDelta the delivered cost less the least-cost one
 * @param totalSecondsDelta the jobs' total time less the least-cost one. Positive is longer.
 * @param longestJobSecondsSaved how much shorter the longest job is. Positive is faster, negative is slower.
 * @param costPerHourSaved the extra delivered cost for each hour the longest job is shortened, or null when it is not
 *         shortened
 */
@ValueObject
public record SplitComparison(
        ProjectCost chosen, ProjectCost leastCost, List<CostLine> lineDeltas, BigDecimal deliveredCostDelta,
        long totalSecondsDelta, long longestJobSecondsSaved, BigDecimal costPerHourSaved) {

    public SplitComparison {
        lineDeltas = List.copyOf(lineDeltas);
    }
}
