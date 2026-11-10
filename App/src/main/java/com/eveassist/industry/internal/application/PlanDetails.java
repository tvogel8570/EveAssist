package com.eveassist.industry.internal.application;

import com.eveassist.industry.PlanId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PlanDetails(
        PlanId id,
        String name,
        long version,
        List<PlanProductLine> products,
        List<PlanLineCosting> bought,
        List<PlanLineCosting> parts,
        BigDecimal totalCost,
        int unpricedLines,
        Instant pricesAsOf,
        String sdeVersion,
        boolean archived) {

    public PlanDetails {
        products = List.copyOf(products);
        bought = List.copyOf(bought);
        parts = List.copyOf(parts);
    }
}
