package archfixture.clean.industry;

import archfixture.clean.identity.Viewer;
import archfixture.clean.industry.internal.application.PlanQueries;

@org.springframework.stereotype.Service
public class IndustryService {
    private final PlanQueries queries;

    IndustryService(PlanQueries queries) {
        this.queries = queries;
    }

    public void find(Viewer viewer, PlanId id) {
        queries.find(viewer, id);
    }
}
