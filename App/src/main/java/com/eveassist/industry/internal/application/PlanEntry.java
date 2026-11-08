package com.eveassist.industry.internal.application;

import com.eveassist.identity.Viewer;
import com.eveassist.industry.PlanId;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Actions on opening a page, which may do more than read. Plans are costed here rather than in the background. */
@Service
@PrimaryPort
@Transactional
public class PlanEntry {

    private final PlanRepository plans;
    private final PlanCostings costings;

    PlanEntry(PlanRepository plans, PlanCostings costings) {
        this.plans = plans;
        this.costings = costings;
    }

    /** The plan is costed to draw the page anyway, so an out-of-date stored total is replaced at the same time. */
    public PlanDetails open(Viewer viewer, PlanId id) {
        var plan = plans.find(viewer, id).orElseThrow(PlanNotFoundException::new);
        var inputs = costings.inputs(plan.products());
        var cost = inputs.cost(plan);
        if (!plan.products().isEmpty()) {
            plans.recordCosting(viewer, plan, costings.total(cost, inputs));
        }
        return costings.details(plan, plan.version(), inputs, cost);
    }
}
