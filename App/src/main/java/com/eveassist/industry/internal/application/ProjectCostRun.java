package com.eveassist.industry.internal.application;

/**
 * A project costed when the developer asked, after the adjusted prices were refreshed.
 *
 * @param adjustedPricesProblem why the adjusted prices could not be refreshed, or null. The costing then used the ones that were stored,
 *         and {@link ProjectCostResult#adjustedPricesAsOf()} says how old they are
 */
public record ProjectCostRun(ProjectCostResult result, String adjustedPricesProblem) {
}
