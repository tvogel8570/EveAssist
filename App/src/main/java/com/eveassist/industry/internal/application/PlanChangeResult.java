package com.eveassist.industry.internal.application;

import com.eveassist.industry.PlanId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** The neutral facts of an edit. A page picks what to redraw from them. */
public record PlanChangeResult(
        PlanId planId,
        long version,
        List<PlanLineCosting> changedLines,
        List<PlanRunLine> runs,
        BigDecimal totalCost,
        int unpricedLines,
        Instant pricesAsOf) {

    public PlanChangeResult {
        changedLines = List.copyOf(changedLines);
        runs = List.copyOf(runs);
    }
}
