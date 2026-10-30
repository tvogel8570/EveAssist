package com.eveassist.spike.industry.internal.application;

import com.eveassist.spike.identity.Viewer;
import com.eveassist.spike.industry.PlanId;
import com.eveassist.spike.industry.PlanNotFoundException;
import com.eveassist.spike.industry.internal.domain.Plan;
import java.util.List;
import org.springframework.stereotype.Service;

/** The only class allowed to use PlanRepository, so a plan cannot be loaded without saying who is asking. */
@Service
public class VisiblePlans {
    private final PlanRepository plans;

    VisiblePlans(PlanRepository plans) {
        this.plans = plans;
    }

    public Plan require(Viewer viewer, PlanId id) {
        return plans.findById(id).filter(plan -> plan.isVisibleTo(viewer))
                .orElseThrow(() -> new PlanNotFoundException(id));
    }

    public List<Plan> all(Viewer viewer) {
        return plans.findByOwner(viewer.accountId());
    }
}
