package com.eveassist.spike.industry.internal.application;

import com.eveassist.spike.identity.Viewer;
import com.eveassist.spike.industry.PlanId;
import com.eveassist.spike.industry.PlanNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class PlanVisibility {
    private final PlanRepository plans;

    PlanVisibility(PlanRepository plans) {
        this.plans = plans;
    }

    public void check(Viewer viewer, PlanId id) {
        plans.findById(id).filter(plan -> plan.isVisibleTo(viewer)).orElseThrow(() -> new PlanNotFoundException(id));
    }
}
