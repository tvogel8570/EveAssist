package com.eveassist.spike.industry.internal.application;

import com.eveassist.spike.industry.PlanId;

/** Screen-only: the plan list shows totals, which the API keeps in its costing resource. */
public record PlanListRow(PlanId id, String name, int lineCount, long totalCost) {
}
