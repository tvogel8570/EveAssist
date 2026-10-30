package com.eveassist.spike.api;

import com.eveassist.spike.industry.PlanCosting;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

record PlanCostingResponse(UUID planId, List<LineCost> lines, long totalCost, Instant pricesAsOf) {
    static PlanCostingResponse from(PlanCosting costing) {
        return new PlanCostingResponse(costing.id().value(), costing.lines().stream()
                .map(l -> new LineCost(l.lineId(), l.unitPrice(), l.lineCost())).toList(),
                costing.totalCost(), costing.pricesAsOf());
    }

    record LineCost(UUID lineId, long unitPrice, long lineCost) {
    }
}
