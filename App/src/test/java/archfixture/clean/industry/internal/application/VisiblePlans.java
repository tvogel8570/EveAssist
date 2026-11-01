package archfixture.clean.industry.internal.application;

import archfixture.clean.identity.Viewer;

public class VisiblePlans {
    private final PlanRepository plans;

    VisiblePlans(PlanRepository plans) {
        this.plans = plans;
    }

    public void load(Viewer viewer, archfixture.clean.industry.PlanId id) {}
}
