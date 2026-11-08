package archfixture.clean.industry.internal.persistence;

import archfixture.clean.identity.Viewer;
import archfixture.clean.industry.internal.application.PlanRepository;

public class JdbcPlanRepository implements PlanRepository {

    @Override
    public String find(Viewer viewer, String planId) {
        return planId;
    }
}
