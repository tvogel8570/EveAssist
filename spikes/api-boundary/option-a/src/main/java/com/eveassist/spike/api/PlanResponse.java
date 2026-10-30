package com.eveassist.spike.api;

import com.eveassist.spike.industry.PlanDetails;
import java.util.List;
import java.util.UUID;

record PlanResponse(UUID id, String name, List<Line> lines) {
    static PlanResponse from(PlanDetails plan) {
        return new PlanResponse(plan.id().value(), plan.name(), plan.lines().stream()
                .map(l -> new Line(l.lineId(), l.itemName(), l.quantity())).toList());
    }

    record Line(UUID id, String itemName, int quantity) {
    }
}
