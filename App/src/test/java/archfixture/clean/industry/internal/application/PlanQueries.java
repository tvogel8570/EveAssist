package archfixture.clean.industry.internal.application;

import archfixture.clean.identity.Viewer;

public class PlanQueries {
    private final VisiblePlans plans;

    PlanQueries(VisiblePlans plans) {
        this.plans = plans;
    }

    public void find(Viewer viewer, archfixture.clean.industry.PlanId id) {
        plans.load(viewer, id);
    }

    public static PlanQueries none() {
        return null;
    }
}
