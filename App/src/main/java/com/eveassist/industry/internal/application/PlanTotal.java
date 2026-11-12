package com.eveassist.industry.internal.application;

import java.math.BigDecimal;
import java.time.Instant;

/** A costing's total and where it came from, stored with the plan. */
public record PlanTotal(BigDecimal totalCost, String sdeVersion, Instant pricesAsOf) {
}
