package com.eveassist.spike.api;

import com.eveassist.spike.industry.PlanSummary;
import java.util.UUID;

record PlanSummaryResponse(UUID id, String name, int lineCount) {
    static PlanSummaryResponse from(PlanSummary plan) {
        return new PlanSummaryResponse(plan.id().value(), plan.name(), plan.lineCount());
    }
}
