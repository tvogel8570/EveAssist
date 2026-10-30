package com.eveassist.fixture.industry.internal.application;

import com.eveassist.spike.identity.Viewer;
import com.eveassist.spike.industry.PlanId;
import com.eveassist.spike.industry.internal.application.PlanRepository;
import org.springframework.stereotype.Service;

/** Has the viewer but loads the plan anyway. Breaks the rule on purpose. */
@Service
public class ViewerIgnoringUseCase {
    private final PlanRepository plans;

    ViewerIgnoringUseCase(PlanRepository plans) {
        this.plans = plans;
    }

    public Object load(Viewer viewer, PlanId id) {
        return plans.findById(id).orElseThrow();
    }
}
