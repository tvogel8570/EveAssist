package archfixture.clean.industry.internal.application;

import archfixture.clean.identity.Viewer;

public class PlanQueries {

    private final PlanRepository plans;

    PlanQueries(PlanRepository plans) {
        this.plans = plans;
    }

    public void find(Viewer viewer, archfixture.clean.industry.PlanId id) {
        plans.find(viewer, id.value().toString());
    }

    public static PlanQueries none() {
        return null;
    }
}
