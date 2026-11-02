package com.eveassist.industry.internal.application;

import java.math.BigDecimal;
import com.eveassist.sde.TypeId;
import java.time.Instant;
import java.util.Set;

/** A costing's total and where it came from, stored with the plan. */
public record PlanTotal(BigDecimal totalCost, String sdeVersion, Instant pricesAsOf, Set<TypeId> pricedItems) {

    public PlanTotal {
        pricedItems = Set.copyOf(pricedItems);
    }
}
