package com.eveassist.industry.internal.application;

import com.eveassist.industry.PlanId;
import java.math.BigDecimal;
import java.time.Instant;

/** A plan with its last-costed total, which may be out of date. The list never recalculates it. */
public record PlanOverview(
        PlanId id, String name, long productCount, BigDecimal totalCost, Instant costedAt, boolean outOfDate) {
}
