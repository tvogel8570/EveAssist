package com.eveassist.spike.industry.internal.application;

import com.eveassist.spike.industry.ChangeLineQuantityCommand;
import com.eveassist.spike.industry.PlanChange;
import com.eveassist.spike.industry.PlanNotFoundException;
import com.eveassist.spike.industry.internal.domain.Plan;
import com.eveassist.spike.industry.internal.domain.PlanLine;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ChangeLineQuantity {
    private final PlanRepository plans;

    ChangeLineQuantity(PlanRepository plans) {
        this.plans = plans;
    }

    public PlanChange handle(ChangeLineQuantityCommand command) {
        Plan plan = plans.findById(command.planId()).orElseThrow(() -> new PlanNotFoundException(command.planId()));
        PlanLine changed = plan.changeLineQuantity(command.lineId(), command.quantity());
        return new PlanChange(plan.id(), List.of(PlanQueries.row(changed)), plan.totalCost());
    }
}
