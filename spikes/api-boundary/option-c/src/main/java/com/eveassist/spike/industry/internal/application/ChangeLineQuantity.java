package com.eveassist.spike.industry.internal.application;

import com.eveassist.spike.identity.Viewer;
import com.eveassist.spike.industry.internal.domain.Plan;
import com.eveassist.spike.industry.internal.domain.PlanLine;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ChangeLineQuantity {
    private final VisiblePlans plans;

    ChangeLineQuantity(VisiblePlans plans) {
        this.plans = plans;
    }

    public PlanChange handle(Viewer viewer, ChangeLineQuantityCommand command) {
        Plan plan = plans.require(viewer, command.planId());
        PlanLine changed = plan.changeLineQuantity(command.lineId(), command.quantity());
        return new PlanChange(plan.id(), List.of(PlanQueries.row(changed)), plan.totalCost());
    }
}
